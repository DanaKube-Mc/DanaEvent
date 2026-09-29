package fr.danakube.danaevent.modules.boatrace.database;

import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.modules.boatrace.model.HudType;
import fr.danakube.danaevent.modules.boatrace.model.RecordEntry;
import org.bukkit.Material;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Handles database operations and table schema for the BoatRace module records and player preferences.
 */
public class BoatRaceDatabase {

    private final DatabaseManager databaseManager;
    private final Executor asyncExecutor;

    public BoatRaceDatabase(DatabaseManager databaseManager) {
        this(databaseManager, ForkJoinPool.commonPool());
    }

    public BoatRaceDatabase(DatabaseManager databaseManager, Executor asyncExecutor) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "DatabaseManager cannot be null");
        this.asyncExecutor = asyncExecutor != null ? asyncExecutor : ForkJoinPool.commonPool();
    }

    /**
     * Initializes the boatrace SQL table and indexes in SQLite and MySQL compatible syntax.
     *
     * @throws SQLException if a database error occurs during table creation
     */
    public void initTables() throws SQLException {
        try (Connection connection = databaseManager.getDataSource().getConnection();
             Statement statement = connection.createStatement()) {

            if (databaseManager.getConfig().type() == StorageType.SQLITE) {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_boatrace_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        track_id VARCHAR(64) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        time_millis BIGINT NOT NULL,
                        laps INT NOT NULL,
                        period_month VARCHAR(32) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """);
                statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_boatrace_records_track_period_time
                    ON dana_boatrace_records (track_id, period_month, time_millis);
                """);
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_boatrace_preferences (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        boat_material VARCHAR(32) NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """);
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_boatrace_hud_preferences (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        hud_type VARCHAR(16) NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """);
            } else {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_boatrace_records (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        track_id VARCHAR(64) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        time_millis BIGINT NOT NULL,
                        laps INT NOT NULL,
                        period_month VARCHAR(32) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_boatrace_records_track_period_time (track_id, period_month, time_millis)
                    );
                """);
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_boatrace_preferences (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        boat_material VARCHAR(32) NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """);
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_boatrace_hud_preferences (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        hud_type VARCHAR(16) NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """);
            }
        }
    }

    /**
     * Asynchronously saves a new race record entry into the database.
     *
     * @param record the record to persist
     * @return CompletableFuture completing when inserted
     */
    public CompletableFuture<Void> saveRecord(RecordEntry record) {
        return insertRecord(record);
    }

    /**
     * Asynchronously saves a new race record entry into the database by parameters.
     *
     * @param trackId track identifier
     * @param playerUuid player UUID
     * @param timeMillis elapsed race time in ms
     * @param laps laps completed
     * @return CompletableFuture completing when inserted
     */
    public CompletableFuture<Void> saveRecord(String trackId, UUID playerUuid, long timeMillis, int laps) {
        String periodMonth = java.time.YearMonth.now().toString();
        return insertRecord(new RecordEntry(trackId, playerUuid, timeMillis, laps, periodMonth, Instant.now()));
    }

    /**
     * Asynchronously inserts a new race record entry into the database.
     *
     * @param record the record to persist
     * @return CompletableFuture completing when inserted
     */
    public CompletableFuture<Void> insertRecord(RecordEntry record) {
        Objects.requireNonNull(record, "record cannot be null");

        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO dana_boatrace_records (track_id, player_uuid, time_millis, laps, period_month, created_at)
                VALUES (?, ?, ?, ?, ?, ?);
            """;

            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, record.trackId());
                statement.setString(2, record.playerUuid().toString());
                statement.setLong(3, record.timeMillis());
                statement.setInt(4, record.laps());
                statement.setString(5, record.periodMonth());
                statement.setTimestamp(6, Timestamp.from(record.createdAt()));
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Asynchronously retrieves the top leaderboard records for a track and period,
     * ordered by time ascending (fastest first).
     *
     * @param trackId track identifier
     * @param periodMonth period key (e.g. "2026-09" or "ALL_TIME")
     * @param limit maximum number of records to return
     * @return CompletableFuture containing ordered list of top records
     */
    public CompletableFuture<List<RecordEntry>> getTopRecords(String trackId, String periodMonth, int limit) {
        return getTopMonthly(trackId, periodMonth, limit);
    }

    /**
     * Asynchronously retrieves the top monthly records for a track and period,
     * ordered by time ascending (fastest first).
     *
     * @param trackId track identifier
     * @param periodMonth period key (e.g. "2026-09")
     * @param limit maximum number of records to return
     * @return CompletableFuture containing ordered list of top records
     */
    public CompletableFuture<List<RecordEntry>> getTopMonthly(String trackId, String periodMonth, int limit) {
        Objects.requireNonNull(trackId, "trackId cannot be null");
        Objects.requireNonNull(periodMonth, "periodMonth cannot be null");
        int effectiveLimit = Math.max(1, limit);

        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT id, track_id, player_uuid, time_millis, laps, period_month, created_at
                FROM (
                    SELECT id, track_id, player_uuid, time_millis, laps, period_month, created_at,
                           ROW_NUMBER() OVER (PARTITION BY player_uuid ORDER BY time_millis ASC, created_at ASC, id ASC) AS rn
                    FROM dana_boatrace_records
                    WHERE track_id = ? AND period_month = ?
                ) ranked
                WHERE rn = 1
                ORDER BY time_millis ASC, created_at ASC
                LIMIT ?;
            """;

            List<RecordEntry> records = new ArrayList<>();
            Set<UUID> seenPlayers = new HashSet<>();
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, trackId);
                statement.setString(2, periodMonth);
                statement.setInt(3, effectiveLimit);

                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next() && records.size() < effectiveLimit) {
                        RecordEntry entry = mapResultSetToRecord(rs);
                        if (seenPlayers.add(entry.playerUuid())) {
                            records.add(entry);
                        }
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return records;
        }, asyncExecutor);
    }

    /**
     * Asynchronously retrieves the top all-time records for a track across all periods,
     * ordered by time ascending (fastest first), keeping only the best time per player.
     *
     * @param trackId track identifier
     * @param limit maximum number of records to return
     * @return CompletableFuture containing ordered list of top records
     */
    public CompletableFuture<List<RecordEntry>> getTopAllTime(String trackId, int limit) {
        Objects.requireNonNull(trackId, "trackId cannot be null");
        int effectiveLimit = Math.max(1, limit);

        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT id, track_id, player_uuid, time_millis, laps, period_month, created_at
                FROM (
                    SELECT id, track_id, player_uuid, time_millis, laps, period_month, created_at,
                           ROW_NUMBER() OVER (PARTITION BY player_uuid ORDER BY time_millis ASC, created_at ASC, id ASC) AS rn
                    FROM dana_boatrace_records
                    WHERE track_id = ?
                ) ranked
                WHERE rn = 1
                ORDER BY time_millis ASC, created_at ASC
                LIMIT ?;
            """;

            List<RecordEntry> records = new ArrayList<>();
            Set<UUID> seenPlayers = new HashSet<>();
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, trackId);
                statement.setInt(2, effectiveLimit);

                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next() && records.size() < effectiveLimit) {
                        RecordEntry entry = mapResultSetToRecord(rs);
                        if (seenPlayers.add(entry.playerUuid())) {
                            records.add(entry);
                        }
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return records;
        }, asyncExecutor);
    }

    /**
     * Asynchronously retrieves the player's personal best (all-time) record on a track.
     *
     * @param trackId track identifier
     * @param playerUuid player UUID
     * @return CompletableFuture containing Optional of the fastest record
     */
    public CompletableFuture<Optional<RecordEntry>> getPersonalBest(String trackId, UUID playerUuid) {
        Objects.requireNonNull(trackId, "trackId cannot be null");
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");

        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT id, track_id, player_uuid, time_millis, laps, period_month, created_at
                FROM dana_boatrace_records
                WHERE track_id = ? AND player_uuid = ?
                ORDER BY time_millis ASC
                LIMIT 1;
            """;

            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, trackId);
                statement.setString(2, playerUuid.toString());

                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToRecord(rs));
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return Optional.empty();
        }, asyncExecutor);
    }

    /**
     * Asynchronously retrieves the player's monthly personal best record on a track.
     *
     * @param trackId track identifier
     * @param playerUuid player UUID
     * @param periodMonth month key (e.g. "2026-09")
     * @return CompletableFuture containing Optional of the monthly fastest record
     */
    public CompletableFuture<Optional<RecordEntry>> getMonthlyPersonalBest(String trackId, UUID playerUuid, String periodMonth) {
        Objects.requireNonNull(trackId, "trackId cannot be null");
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(periodMonth, "periodMonth cannot be null");

        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT id, track_id, player_uuid, time_millis, laps, period_month, created_at
                FROM dana_boatrace_records
                WHERE track_id = ? AND player_uuid = ? AND period_month = ?
                ORDER BY time_millis ASC
                LIMIT 1;
            """;

            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, trackId);
                statement.setString(2, playerUuid.toString());
                statement.setString(3, periodMonth);

                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToRecord(rs));
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return Optional.empty();
        }, asyncExecutor);
    }

    /**
     * Asynchronously resets (deletes) ranking records for a track and optional period.
     * If periodMonth is null, empty or "ALL", removes all records for that track.
     *
     * @param trackId track identifier
     * @param periodMonth period key (e.g. "2026-09") or null/"ALL"
     * @return CompletableFuture containing the number of deleted records
     */
    public CompletableFuture<Integer> resetRanking(String trackId, String periodMonth) {
        Objects.requireNonNull(trackId, "trackId cannot be null");

        return CompletableFuture.supplyAsync(() -> {
            boolean specificMonth = periodMonth != null && !periodMonth.isBlank() && !periodMonth.equalsIgnoreCase("ALL");
            String sql = specificMonth
                ? "DELETE FROM dana_boatrace_records WHERE track_id = ? AND period_month = ?;"
                : "DELETE FROM dana_boatrace_records WHERE track_id = ?;";

            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, trackId);
                if (specificMonth) {
                    statement.setString(2, periodMonth);
                }

                return statement.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    private RecordEntry mapResultSetToRecord(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String tId = rs.getString("track_id");
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        long timeMillis = rs.getLong("time_millis");
        int laps = rs.getInt("laps");
        String pMonth = rs.getString("period_month");
        Timestamp ts = rs.getTimestamp("created_at");
        Instant createdAt = ts != null ? ts.toInstant() : Instant.now();
        return new RecordEntry(id, tId, playerUuid, timeMillis, laps, pMonth, createdAt);
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Asynchronously retrieves the saved boat material preference for a player.
     *
     * @param uuid player unique identifier
     * @return CompletableFuture containing Optional with Material, or empty if unset
     */
    public CompletableFuture<Optional<Material>> getPlayerBoatPreference(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid cannot be null");

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT boat_material FROM dana_boatrace_preferences WHERE player_uuid = ?;";
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, uuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        String matName = rs.getString("boat_material");
                        Material material = Material.matchMaterial(matName);
                        return Optional.ofNullable(material);
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return Optional.empty();
        }, asyncExecutor);
    }

    /**
     * Asynchronously sets or updates the saved boat material preference for a player.
     *
     * @param uuid player unique identifier
     * @param material preferred boat material
     * @return CompletableFuture completing when updated
     */
    public CompletableFuture<Void> setPlayerBoatPreference(UUID uuid, Material material) {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(material, "material cannot be null");

        return CompletableFuture.runAsync(() -> {
            String sql;
            if (databaseManager.getConfig().type() == StorageType.SQLITE) {
                sql = """
                    INSERT INTO dana_boatrace_preferences (player_uuid, boat_material, updated_at)
                    VALUES (?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(player_uuid) DO UPDATE SET boat_material = excluded.boat_material, updated_at = CURRENT_TIMESTAMP;
                """;
            } else {
                sql = """
                    INSERT INTO dana_boatrace_preferences (player_uuid, boat_material, updated_at)
                    VALUES (?, ?, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE boat_material = VALUES(boat_material), updated_at = CURRENT_TIMESTAMP;
                """;
            }

            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, uuid.toString());
                statement.setString(2, material.name());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Asynchronously retrieves the saved HUD type preference for a player.
     *
     * @param uuid player unique identifier
     * @return CompletableFuture containing Optional with HudType, or empty if unset
     */
    public CompletableFuture<Optional<HudType>> getPlayerHudPreference(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid cannot be null");

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT hud_type FROM dana_boatrace_hud_preferences WHERE player_uuid = ?;";
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, uuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        String typeName = rs.getString("hud_type");
                        HudType hudType = HudType.fromString(typeName);
                        return Optional.ofNullable(hudType);
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return Optional.empty();
        }, asyncExecutor);
    }

    /**
     * Asynchronously sets or updates the saved HUD type preference for a player.
     *
     * @param uuid player unique identifier
     * @param hudType preferred HUD type
     * @return CompletableFuture completing when updated
     */
    public CompletableFuture<Void> setPlayerHudPreference(UUID uuid, HudType hudType) {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(hudType, "hudType cannot be null");

        return CompletableFuture.runAsync(() -> {
            String sql;
            if (databaseManager.getConfig().type() == StorageType.SQLITE) {
                sql = """
                    INSERT INTO dana_boatrace_hud_preferences (player_uuid, hud_type, updated_at)
                    VALUES (?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(player_uuid) DO UPDATE SET hud_type = excluded.hud_type, updated_at = CURRENT_TIMESTAMP;
                """;
            } else {
                sql = """
                    INSERT INTO dana_boatrace_hud_preferences (player_uuid, hud_type, updated_at)
                    VALUES (?, ?, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE hud_type = VALUES(hud_type), updated_at = CURRENT_TIMESTAMP;
                """;
            }

            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, uuid.toString());
                statement.setString(2, hudType.name());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }
}

