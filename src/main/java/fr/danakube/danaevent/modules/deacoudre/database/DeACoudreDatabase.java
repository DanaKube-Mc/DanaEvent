package fr.danakube.danaevent.modules.deacoudre.database;

import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.modules.deacoudre.model.DacRecord;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Handles database operations and asynchronous persistence for Dé à Coudre records.
 */
public class DeACoudreDatabase {

    private final DatabaseManager databaseManager;
    private final Executor asyncExecutor;

    public DeACoudreDatabase(@NotNull DatabaseManager databaseManager) {
        this(databaseManager, ForkJoinPool.commonPool());
    }

    public DeACoudreDatabase(@NotNull DatabaseManager databaseManager, @NotNull Executor asyncExecutor) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor cannot be null");
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Initializes database tables and indexes.
     */
    public void initTables() throws SQLException {
        try (Connection connection = databaseManager.getDataSource().getConnection();
             Statement statement = connection.createStatement()) {

            if (databaseManager.getConfig().type() == StorageType.SQLITE) {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_deacoudre_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        arena_id VARCHAR(32) NOT NULL,
                        holder_uuid VARCHAR(36) NOT NULL,
                        is_team BOOLEAN NOT NULL DEFAULT 0,
                        wins INT NOT NULL DEFAULT 0,
                        successful_jumps INT NOT NULL DEFAULT 0,
                        perfect_dacs INT NOT NULL DEFAULT 0,
                        period_month VARCHAR(7) NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE (arena_id, holder_uuid, period_month)
                    );
                """);
                statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_dac_ranking
                    ON dana_deacoudre_records (arena_id, period_month, wins, perfect_dacs);
                """);
            } else {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_deacoudre_records (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        arena_id VARCHAR(32) NOT NULL,
                        holder_uuid VARCHAR(36) NOT NULL,
                        is_team BOOLEAN NOT NULL DEFAULT FALSE,
                        wins INT NOT NULL DEFAULT 0,
                        successful_jumps INT NOT NULL DEFAULT 0,
                        perfect_dacs INT NOT NULL DEFAULT 0,
                        period_month VARCHAR(7) NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        UNIQUE KEY uq_dac_arena_holder_month (arena_id, holder_uuid, period_month),
                        INDEX idx_dac_ranking (arena_id, period_month, wins, perfect_dacs)
                    );
                """);
            }
        }
    }

    /**
     * Asynchronously records or increments match stats for a player or team in an arena using current month.
     */
    public CompletableFuture<Void> recordMatchResult(
        @NotNull String arenaId,
        @NotNull UUID holderUuid,
        boolean isTeam,
        boolean won,
        int successfulJumps,
        int perfectDacs
    ) {
        String currentMonth = java.time.YearMonth.now().toString();
        return recordMatchResult(arenaId, holderUuid, isTeam, won, successfulJumps, perfectDacs, currentMonth);
    }

    /**
     * Asynchronously records or increments match stats for a player or team in an arena.
     */
    public CompletableFuture<Void> recordMatchResult(
        @NotNull String arenaId,
        @NotNull UUID holderUuid,
        boolean isTeam,
        boolean won,
        int successfulJumps,
        int perfectDacs,
        @NotNull String periodMonth
    ) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");
        Objects.requireNonNull(periodMonth, "periodMonth cannot be null");

        int addWins = won ? 1 : 0;

        return CompletableFuture.runAsync(() -> {
            boolean isSqlite = databaseManager.getConfig().type() == StorageType.SQLITE;
            String sql;
            if (isSqlite) {
                sql = """
                    INSERT INTO dana_deacoudre_records
                    (arena_id, holder_uuid, is_team, wins, successful_jumps, perfect_dacs, period_month, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(arena_id, holder_uuid, period_month) DO UPDATE SET
                        wins = wins + excluded.wins,
                        successful_jumps = successful_jumps + excluded.successful_jumps,
                        perfect_dacs = perfect_dacs + excluded.perfect_dacs,
                        updated_at = excluded.updated_at;
                """;
            } else {
                sql = """
                    INSERT INTO dana_deacoudre_records
                    (arena_id, holder_uuid, is_team, wins, successful_jumps, perfect_dacs, period_month, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        wins = wins + VALUES(wins),
                        successful_jumps = successful_jumps + VALUES(successful_jumps),
                        perfect_dacs = perfect_dacs + VALUES(perfect_dacs),
                        updated_at = VALUES(updated_at);
                """;
            }

            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, arenaId.trim().toLowerCase());
                statement.setString(2, holderUuid.toString());
                statement.setBoolean(3, isTeam);
                statement.setInt(4, addWins);
                statement.setInt(5, Math.max(0, successfulJumps));
                statement.setInt(6, Math.max(0, perfectDacs));
                statement.setString(7, periodMonth);
                statement.setTimestamp(8, Timestamp.from(Instant.now()));

                statement.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Asynchronously retrieves top monthly rankings for an arena.
     */
    public CompletableFuture<List<DacRecord>> getTopMonthly(
        @NotNull String arenaId,
        @NotNull String periodMonth,
        int limit
    ) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        Objects.requireNonNull(periodMonth, "periodMonth cannot be null");
        int effectiveLimit = Math.max(1, limit);

        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT id, arena_id, holder_uuid, is_team, wins, successful_jumps, perfect_dacs, period_month, updated_at
                FROM dana_deacoudre_records
                WHERE arena_id = ? AND period_month = ?
                ORDER BY wins DESC, perfect_dacs DESC, successful_jumps DESC, updated_at ASC
                LIMIT ?;
            """;

            List<DacRecord> records = new ArrayList<>();
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, arenaId.trim().toLowerCase());
                statement.setString(2, periodMonth);
                statement.setInt(3, effectiveLimit);

                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        records.add(mapRecord(rs));
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return records;
        }, asyncExecutor);
    }

    /**
     * Asynchronously retrieves top all-time rankings for an arena, aggregated across all periods.
     */
    public CompletableFuture<List<DacRecord>> getTopAllTime(
        @NotNull String arenaId,
        int limit
    ) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        int effectiveLimit = Math.max(1, limit);

        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT 0 AS id, arena_id, holder_uuid, is_team,
                       SUM(wins) AS total_wins,
                       SUM(successful_jumps) AS total_jumps,
                       SUM(perfect_dacs) AS total_perfects,
                       'ALL_TIME' AS period_month,
                       MAX(updated_at) AS last_updated
                FROM dana_deacoudre_records
                WHERE arena_id = ?
                GROUP BY arena_id, holder_uuid, is_team
                ORDER BY total_wins DESC, total_perfects DESC, total_jumps DESC
                LIMIT ?;
            """;

            List<DacRecord> records = new ArrayList<>();
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, arenaId.trim().toLowerCase());
                statement.setInt(2, effectiveLimit);

                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        String aId = rs.getString("arena_id");
                        UUID holder = UUID.fromString(rs.getString("holder_uuid"));
                        boolean isTeam = rs.getBoolean("is_team");
                        int wins = rs.getInt("total_wins");
                        int jumps = rs.getInt("total_jumps");
                        int perfects = rs.getInt("total_perfects");
                        String month = rs.getString("period_month");
                        Timestamp ts = rs.getTimestamp("last_updated");
                        Instant updated = ts != null ? ts.toInstant() : Instant.now();

                        records.add(new DacRecord(id, aId, holder, isTeam, wins, jumps, perfects, month, updated));
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return records;
        }, asyncExecutor);
    }

    /**
     * Retrieves aggregated personal stats for a player or team in an arena.
     */
    public CompletableFuture<Optional<DacRecord>> getPersonalStats(@NotNull String arenaId, @NotNull UUID holderUuid) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");

        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT 0 AS id, arena_id, holder_uuid, is_team,
                       SUM(wins) AS total_wins,
                       SUM(successful_jumps) AS total_jumps,
                       SUM(perfect_dacs) AS total_perfects,
                       'ALL_TIME' AS period_month,
                       MAX(updated_at) AS last_updated
                FROM dana_deacoudre_records
                WHERE arena_id = ? AND holder_uuid = ?
                GROUP BY arena_id, holder_uuid, is_team;
            """;

            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, arenaId.trim().toLowerCase());
                statement.setString(2, holderUuid.toString());

                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        long id = rs.getLong("id");
                        String aId = rs.getString("arena_id");
                        UUID holder = UUID.fromString(rs.getString("holder_uuid"));
                        boolean isTeam = rs.getBoolean("is_team");
                        int wins = rs.getInt("total_wins");
                        int jumps = rs.getInt("total_jumps");
                        int perfects = rs.getInt("total_perfects");
                        String month = rs.getString("period_month");
                        Timestamp ts = rs.getTimestamp("last_updated");
                        Instant updated = ts != null ? ts.toInstant() : Instant.now();

                        return Optional.of(new DacRecord(id, aId, holder, isTeam, wins, jumps, perfects, month, updated));
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return Optional.empty();
        }, asyncExecutor);
    }

    /**
     * Asynchronously retrieves the total number of Perfect DACs performed by a player across all arenas.
     */
    public CompletableFuture<Integer> getPlayerTotalPerfects(@NotNull UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COALESCE(SUM(perfect_dacs), 0) FROM dana_deacoudre_records WHERE holder_uuid = ?;";
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, playerUuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return 0;
        }, asyncExecutor);
    }

    /**
     * Resets ranking records for an arena, optionally for a specific period.
     */
    public CompletableFuture<Integer> resetRanking(@NotNull String arenaId, @Nullable String periodMonth) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");

        return CompletableFuture.supplyAsync(() -> {
            String sql;
            if (periodMonth == null || periodMonth.isBlank() || "ALL".equalsIgnoreCase(periodMonth) || "ALL_TIME".equalsIgnoreCase(periodMonth)) {
                sql = "DELETE FROM dana_deacoudre_records WHERE arena_id = ?;";
            } else {
                sql = "DELETE FROM dana_deacoudre_records WHERE arena_id = ? AND period_month = ?;";
            }

            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, arenaId.trim().toLowerCase());
                if (periodMonth != null && !periodMonth.isBlank() && !"ALL".equalsIgnoreCase(periodMonth) && !"ALL_TIME".equalsIgnoreCase(periodMonth)) {
                    statement.setString(2, periodMonth);
                }
                return statement.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    private DacRecord mapRecord(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String arenaId = rs.getString("arena_id");
        UUID holderUuid = UUID.fromString(rs.getString("holder_uuid"));
        boolean isTeam = rs.getBoolean("is_team");
        int wins = rs.getInt("wins");
        int jumps = rs.getInt("successful_jumps");
        int perfects = rs.getInt("perfect_dacs");
        String periodMonth = rs.getString("period_month");
        Timestamp ts = rs.getTimestamp("updated_at");
        Instant updated = ts != null ? ts.toInstant() : Instant.now();

        return new DacRecord(id, arenaId, holderUuid, isTeam, wins, jumps, perfects, periodMonth, updated);
    }
}
