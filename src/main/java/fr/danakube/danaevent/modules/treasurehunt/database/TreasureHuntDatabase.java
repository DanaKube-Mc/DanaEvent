package fr.danakube.danaevent.modules.treasurehunt.database;

import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntRecord;
import fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.stream.Collectors;

/**
 * Handles database operations for TreasureHunt records and session recovery progress.
 */
public class TreasureHuntDatabase {

    private final DatabaseManager databaseManager;
    private final Executor asyncExecutor;

    public TreasureHuntDatabase(@NotNull DatabaseManager databaseManager) {
        this(databaseManager, ForkJoinPool.commonPool());
    }

    public TreasureHuntDatabase(@NotNull DatabaseManager databaseManager, @NotNull Executor asyncExecutor) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor cannot be null");
    }

    /**
     * Creates database tables and indexes if they do not exist.
     *
     * @throws SQLException if a database error occurs
     */
    public void initTables() throws SQLException {
        try (Connection connection = databaseManager.getDataSource().getConnection();
             Statement statement = connection.createStatement()) {

            if (databaseManager.getConfig().type() == StorageType.SQLITE) {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_treasurehunt_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        hunt_id VARCHAR(32) NOT NULL,
                        holder_uuid VARCHAR(36) NOT NULL,
                        is_team BOOLEAN NOT NULL DEFAULT 0,
                        time_millis BIGINT NOT NULL,
                        period_month VARCHAR(7) NOT NULL,
                        completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """);
                statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_treasurehunt_records_hunt_period_time
                    ON dana_treasurehunt_records (hunt_id, period_month, time_millis);
                """);
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_treasurehunt_progress (
                        holder_uuid VARCHAR(36) PRIMARY KEY,
                        hunt_id VARCHAR(32) NOT NULL,
                        is_team BOOLEAN NOT NULL DEFAULT 0,
                        current_step_index INT NOT NULL DEFAULT 0,
                        step_order TEXT NOT NULL,
                        start_time_millis BIGINT NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """);
            } else {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_treasurehunt_records (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        hunt_id VARCHAR(32) NOT NULL,
                        holder_uuid VARCHAR(36) NOT NULL,
                        is_team BOOLEAN NOT NULL DEFAULT FALSE,
                        time_millis BIGINT NOT NULL,
                        period_month VARCHAR(7) NOT NULL,
                        completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_treasurehunt_records_hunt_period_time (hunt_id, period_month, time_millis)
                    );
                """);
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_treasurehunt_progress (
                        holder_uuid VARCHAR(36) PRIMARY KEY,
                        hunt_id VARCHAR(32) NOT NULL,
                        is_team BOOLEAN NOT NULL DEFAULT FALSE,
                        current_step_index INT NOT NULL DEFAULT 0,
                        step_order TEXT NOT NULL,
                        start_time_millis BIGINT NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    );
                """);
            }
        }
    }

    /**
     * Saves an active hunt progress state to allow recovery upon reconnection.
     *
     * @param progress the player or team progress state
     * @return CompletableFuture completing when saved
     */
    public CompletableFuture<Void> saveProgress(@NotNull PlayerHuntProgress progress) {
        Objects.requireNonNull(progress, "progress cannot be null");

        return CompletableFuture.runAsync(() -> {
            String stepOrderStr = progress.getStepOrder().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

            String sql;
            if (databaseManager.getConfig().type() == StorageType.SQLITE) {
                sql = """
                    INSERT OR REPLACE INTO dana_treasurehunt_progress
                    (holder_uuid, hunt_id, is_team, current_step_index, step_order, start_time_millis, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP);
                """;
            } else {
                sql = """
                    INSERT INTO dana_treasurehunt_progress
                    (holder_uuid, hunt_id, is_team, current_step_index, step_order, start_time_millis, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE
                        hunt_id = VALUES(hunt_id),
                        is_team = VALUES(is_team),
                        current_step_index = VALUES(current_step_index),
                        step_order = VALUES(step_order),
                        start_time_millis = VALUES(start_time_millis),
                        updated_at = CURRENT_TIMESTAMP;
                """;
            }

            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, progress.getHolderUuid().toString());
                statement.setString(2, progress.getHuntId());
                statement.setBoolean(3, progress.isTeam());
                statement.setInt(4, progress.getCurrentStepIndex());
                statement.setString(5, stepOrderStr);
                statement.setLong(6, progress.getStartTimeMillis());

                statement.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Loads active progress for a player or team UUID.
     *
     * @param holderUuid player or team UUID
     * @return CompletableFuture with Optional of PlayerHuntProgress
     */
    public CompletableFuture<Optional<PlayerHuntProgress>> loadProgress(@NotNull UUID holderUuid) {
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT holder_uuid, hunt_id, is_team, current_step_index, step_order, start_time_millis FROM dana_treasurehunt_progress WHERE holder_uuid = ?;";
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, holderUuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        String huntId = rs.getString("hunt_id");
                        boolean isTeam = rs.getBoolean("is_team");
                        int currentStep = rs.getInt("current_step_index");
                        String stepOrderStr = rs.getString("step_order");
                        long startTime = rs.getLong("start_time_millis");

                        List<Integer> steps = Arrays.stream(stepOrderStr.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(Integer::parseInt)
                            .toList();

                        PlayerHuntProgress progress = new PlayerHuntProgress(
                            holderUuid,
                            isTeam,
                            huntId,
                            currentStep,
                            steps,
                            startTime,
                            -1L,
                            false
                        );
                        return Optional.of(progress);
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return Optional.empty();
        }, asyncExecutor);
    }

    /**
     * Deletes saved progress when a hunt is finished or abandoned.
     *
     * @param holderUuid player or team UUID
     * @return CompletableFuture completing when deleted
     */
    public CompletableFuture<Void> deleteProgress(@NotNull UUID holderUuid) {
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");

        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM dana_treasurehunt_progress WHERE holder_uuid = ?;";
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, holderUuid.toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Saves a completed hunt record to the database.
     *
     * @param record the record entry to save
     * @return CompletableFuture completing when inserted
     */
    public CompletableFuture<Void> saveRecord(@NotNull HuntRecord record) {
        Objects.requireNonNull(record, "record cannot be null");

        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO dana_treasurehunt_records
                (hunt_id, holder_uuid, is_team, time_millis, period_month, completed_at)
                VALUES (?, ?, ?, ?, ?, ?);
            """;
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, record.huntId());
                statement.setString(2, record.holderUuid().toString());
                statement.setBoolean(3, record.isTeam());
                statement.setLong(4, record.timeMillis());
                statement.setString(5, record.periodMonth());
                statement.setTimestamp(6, Timestamp.from(record.completedAt()));

                statement.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Asynchronously retrieves the top records for a hunt in a specific period,
     * deduplicated to keep only the best time per player or team.
     *
     * @param huntId hunt identifier
     * @param periodMonth month period (e.g. "2026-09" or "ALL_TIME")
     * @param limit maximum results to return
     * @return CompletableFuture with sorted list of top records
     */
    public CompletableFuture<List<HuntRecord>> getTopRecords(@NotNull String huntId, @NotNull String periodMonth, int limit) {
        Objects.requireNonNull(huntId, "huntId cannot be null");
        Objects.requireNonNull(periodMonth, "periodMonth cannot be null");
        int effectiveLimit = Math.max(1, limit);

        return CompletableFuture.supplyAsync(() -> {
            boolean isAllTime = "ALL_TIME".equalsIgnoreCase(periodMonth);
            String sql;

            if (isAllTime) {
                sql = """
                    SELECT id, hunt_id, holder_uuid, is_team, time_millis, period_month, completed_at
                    FROM (
                        SELECT id, hunt_id, holder_uuid, is_team, time_millis, period_month, completed_at,
                               ROW_NUMBER() OVER (PARTITION BY holder_uuid ORDER BY time_millis ASC, completed_at ASC, id ASC) AS rn
                        FROM dana_treasurehunt_records
                        WHERE hunt_id = ?
                    ) ranked
                    WHERE rn = 1
                    ORDER BY time_millis ASC, completed_at ASC
                    LIMIT ?;
                """;
            } else {
                sql = """
                    SELECT id, hunt_id, holder_uuid, is_team, time_millis, period_month, completed_at
                    FROM (
                        SELECT id, hunt_id, holder_uuid, is_team, time_millis, period_month, completed_at,
                               ROW_NUMBER() OVER (PARTITION BY holder_uuid ORDER BY time_millis ASC, completed_at ASC, id ASC) AS rn
                        FROM dana_treasurehunt_records
                        WHERE hunt_id = ? AND period_month = ?
                    ) ranked
                    WHERE rn = 1
                    ORDER BY time_millis ASC, completed_at ASC
                    LIMIT ?;
                """;
            }

            List<HuntRecord> records = new ArrayList<>();
            Set<UUID> seen = new HashSet<>();
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, huntId.trim().toLowerCase());
                if (isAllTime) {
                    statement.setInt(2, effectiveLimit);
                } else {
                    statement.setString(2, periodMonth);
                    statement.setInt(3, effectiveLimit);
                }

                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next() && records.size() < effectiveLimit) {
                        HuntRecord record = mapRecord(rs);
                        if (seen.add(record.holderUuid())) {
                            records.add(record);
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
     * Retrieves the personal best record for a holder (player or team).
     *
     * @param huntId hunt identifier
     * @param holderUuid holder UUID
     * @return CompletableFuture with Optional of personal best record
     */
    public CompletableFuture<Optional<HuntRecord>> getPersonalBest(@NotNull String huntId, @NotNull UUID holderUuid) {
        Objects.requireNonNull(huntId, "huntId cannot be null");
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");

        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT id, hunt_id, holder_uuid, is_team, time_millis, period_month, completed_at
                FROM dana_treasurehunt_records
                WHERE hunt_id = ? AND holder_uuid = ?
                ORDER BY time_millis ASC, completed_at ASC
                LIMIT 1;
            """;
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, huntId.trim().toLowerCase());
                statement.setString(2, holderUuid.toString());

                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRecord(rs));
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return Optional.empty();
        }, asyncExecutor);
    }

    /**
     * Resets records for a specific hunt, optionally filtering by period.
     *
     * @param huntId hunt identifier
     * @param periodMonth optional period (null or empty resets all periods)
     * @return CompletableFuture with number of deleted rows
     */
    public CompletableFuture<Integer> resetRanking(@NotNull String huntId, String periodMonth) {
        Objects.requireNonNull(huntId, "huntId cannot be null");

        return CompletableFuture.supplyAsync(() -> {
            String sql;
            if (periodMonth == null || periodMonth.isBlank() || "ALL".equalsIgnoreCase(periodMonth)) {
                sql = "DELETE FROM dana_treasurehunt_records WHERE hunt_id = ?;";
            } else {
                sql = "DELETE FROM dana_treasurehunt_records WHERE hunt_id = ? AND period_month = ?;";
            }

            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, huntId.trim().toLowerCase());
                if (periodMonth != null && !periodMonth.isBlank() && !"ALL".equalsIgnoreCase(periodMonth)) {
                    statement.setString(2, periodMonth);
                }
                return statement.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    private HuntRecord mapRecord(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String hId = rs.getString("hunt_id");
        UUID holderUuid = UUID.fromString(rs.getString("holder_uuid"));
        boolean isTeam = rs.getBoolean("is_team");
        long timeMillis = rs.getLong("time_millis");
        String pMonth = rs.getString("period_month");
        Timestamp createdAtTs = rs.getTimestamp("completed_at");
        Instant completedAt = createdAtTs != null ? createdAtTs.toInstant() : Instant.now();

        return new HuntRecord(id, hId, holderUuid, isTeam, timeMillis, pMonth, completedAt);
    }
}
