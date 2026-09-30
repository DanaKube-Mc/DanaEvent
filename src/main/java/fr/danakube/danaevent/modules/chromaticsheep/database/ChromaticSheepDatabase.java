package fr.danakube.danaevent.modules.chromaticsheep.database;

import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepRecord;
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
 * Handles database operations for ChromaticSheep records.
 */
public class ChromaticSheepDatabase {

    private final DatabaseManager databaseManager;
    private final Executor asyncExecutor;

    public ChromaticSheepDatabase(@NotNull DatabaseManager databaseManager) {
        this(databaseManager, ForkJoinPool.commonPool());
    }

    public ChromaticSheepDatabase(@NotNull DatabaseManager databaseManager, @NotNull Executor asyncExecutor) {
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
                    CREATE TABLE IF NOT EXISTS dana_chromaticsheep_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        arena_id VARCHAR(32) NOT NULL,
                        holder_uuid VARCHAR(36) NOT NULL,
                        is_team BOOLEAN NOT NULL DEFAULT 0,
                        score_points INT NOT NULL,
                        sheep_count INT NOT NULL,
                        scoring_mode VARCHAR(16) NOT NULL,
                        period_month VARCHAR(7) NOT NULL,
                        played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """);
                statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_mc_records_arena_period_score
                    ON dana_chromaticsheep_records (arena_id, period_month, score_points);
                """);
            } else {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_chromaticsheep_records (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        arena_id VARCHAR(32) NOT NULL,
                        holder_uuid VARCHAR(36) NOT NULL,
                        is_team BOOLEAN NOT NULL DEFAULT FALSE,
                        score_points INT NOT NULL,
                        sheep_count INT NOT NULL,
                        scoring_mode VARCHAR(16) NOT NULL,
                        period_month VARCHAR(7) NOT NULL,
                        played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_mc_records_arena_period_score (arena_id, period_month, score_points)
                    );
                """);
            }
        }
    }

    /**
     * Asynchronously saves a game record.
     */
    public CompletableFuture<Void> saveRecord(@NotNull SheepRecord record) {
        Objects.requireNonNull(record, "record cannot be null");

        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO dana_chromaticsheep_records
                (arena_id, holder_uuid, is_team, score_points, sheep_count, scoring_mode, period_month, played_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?);
            """;
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, record.arenaId());
                statement.setString(2, record.holderUuid().toString());
                statement.setBoolean(3, record.isTeam());
                statement.setInt(4, record.scorePoints());
                statement.setInt(5, record.sheepCount());
                statement.setString(6, record.scoringMode().name());
                statement.setString(7, record.periodMonth());
                statement.setTimestamp(8, Timestamp.from(record.playedAt()));

                statement.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Asynchronously retrieves top records deduplicated by holder.
     */
    public CompletableFuture<List<SheepRecord>> getTopRecords(
        @NotNull String arenaId,
        @NotNull String periodMonth,
        int limit
    ) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        Objects.requireNonNull(periodMonth, "periodMonth cannot be null");
        int effectiveLimit = Math.max(1, limit);

        return CompletableFuture.supplyAsync(() -> {
            boolean isAllTime = "ALL_TIME".equalsIgnoreCase(periodMonth) || "ALL".equalsIgnoreCase(periodMonth);
            String sql;

            if (isAllTime) {
                sql = """
                    SELECT id, arena_id, holder_uuid, is_team, score_points, sheep_count, scoring_mode, period_month, played_at
                    FROM (
                        SELECT id, arena_id, holder_uuid, is_team, score_points, sheep_count, scoring_mode, period_month, played_at,
                               ROW_NUMBER() OVER (PARTITION BY holder_uuid ORDER BY score_points DESC, sheep_count DESC, played_at ASC, id ASC) AS rn
                        FROM dana_chromaticsheep_records
                        WHERE arena_id = ?
                    ) ranked
                    WHERE rn = 1
                    ORDER BY score_points DESC, sheep_count DESC, played_at ASC
                    LIMIT ?;
                """;
            } else {
                sql = """
                    SELECT id, arena_id, holder_uuid, is_team, score_points, sheep_count, scoring_mode, period_month, played_at
                    FROM (
                        SELECT id, arena_id, holder_uuid, is_team, score_points, sheep_count, scoring_mode, period_month, played_at,
                               ROW_NUMBER() OVER (PARTITION BY holder_uuid ORDER BY score_points DESC, sheep_count DESC, played_at ASC, id ASC) AS rn
                        FROM dana_chromaticsheep_records
                        WHERE arena_id = ? AND period_month = ?
                    ) ranked
                    WHERE rn = 1
                    ORDER BY score_points DESC, sheep_count DESC, played_at ASC
                    LIMIT ?;
                """;
            }

            List<SheepRecord> records = new ArrayList<>();
            Set<UUID> seen = new HashSet<>();
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, arenaId.trim().toLowerCase());
                if (isAllTime) {
                    statement.setInt(2, effectiveLimit);
                } else {
                    statement.setString(2, periodMonth);
                    statement.setInt(3, effectiveLimit);
                }

                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next() && records.size() < effectiveLimit) {
                        SheepRecord record = mapRecord(rs);
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

    public CompletableFuture<List<SheepRecord>> getTopMonthly(@NotNull String arenaId, @NotNull String periodMonth, int limit) {
        return getTopRecords(arenaId, periodMonth, limit);
    }

    public CompletableFuture<List<SheepRecord>> getTopAllTime(@NotNull String arenaId, int limit) {
        return getTopRecords(arenaId, "ALL_TIME", limit);
    }

    /**
     * Retrieves personal best record for a player or team in an arena.
     */
    public CompletableFuture<Optional<SheepRecord>> getPersonalBest(@NotNull String arenaId, @NotNull UUID holderUuid) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");

        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT id, arena_id, holder_uuid, is_team, score_points, sheep_count, scoring_mode, period_month, played_at
                FROM dana_chromaticsheep_records
                WHERE arena_id = ? AND holder_uuid = ?
                ORDER BY score_points DESC, sheep_count DESC, played_at ASC
                LIMIT 1;
            """;
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, arenaId.trim().toLowerCase());
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
     * Resets ranking for an arena, optionally for a specific period.
     */
    public CompletableFuture<Integer> resetRanking(@NotNull String arenaId, @Nullable String periodMonth) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");

        return CompletableFuture.supplyAsync(() -> {
            String sql;
            if (periodMonth == null || periodMonth.isBlank() || "ALL".equalsIgnoreCase(periodMonth) || "ALL_TIME".equalsIgnoreCase(periodMonth)) {
                sql = "DELETE FROM dana_chromaticsheep_records WHERE arena_id = ?;";
            } else {
                sql = "DELETE FROM dana_chromaticsheep_records WHERE arena_id = ? AND period_month = ?;";
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

    private SheepRecord mapRecord(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String arenaId = rs.getString("arena_id");
        UUID holderUuid = UUID.fromString(rs.getString("holder_uuid"));
        boolean isTeam = rs.getBoolean("is_team");
        int scorePoints = rs.getInt("score_points");
        int sheepCount = rs.getInt("sheep_count");
        ScoringMode scoringMode = ScoringMode.fromString(rs.getString("scoring_mode"));
        String periodMonth = rs.getString("period_month");
        Timestamp playedAtTs = rs.getTimestamp("played_at");
        Instant playedAt = playedAtTs != null ? playedAtTs.toInstant() : Instant.now();

        return new SheepRecord(id, arenaId, holderUuid, isTeam, scorePoints, sheepCount, scoringMode, periodMonth, playedAt);
    }
}
