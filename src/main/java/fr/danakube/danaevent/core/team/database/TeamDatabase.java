package fr.danakube.danaevent.core.team.database;

import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import fr.danakube.danaevent.core.team.model.TeamMember;
import fr.danakube.danaevent.core.team.model.TeamRole;
import fr.danakube.danaevent.core.team.model.TeamScoreEntry;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Handles database operations and table schemas for the Team Service,
 * supporting both SQLite and MySQL/MariaDB.
 */
public class TeamDatabase {

    private final DatabaseManager databaseManager;
    private final Executor asyncExecutor;

    public TeamDatabase(@NotNull DatabaseManager databaseManager) {
        this(databaseManager, ForkJoinPool.commonPool());
    }

    public TeamDatabase(@NotNull DatabaseManager databaseManager, @NotNull Executor asyncExecutor) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "DatabaseManager cannot be null");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor cannot be null");
    }

    public @NotNull DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Initializes SQL tables for teams, members, and scores.
     *
     * @throws SQLException if a database error occurs during initialization
     */
    public void initTables() throws SQLException {
        try (Connection connection = databaseManager.getDataSource().getConnection();
             Statement statement = connection.createStatement()) {

            if (databaseManager.getConfig().type() == StorageType.SQLITE) {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_teams (
                        id VARCHAR(32) PRIMARY KEY,
                        display_name VARCHAR(32) NOT NULL,
                        color VARCHAR(16) NOT NULL UNIQUE,
                        leader_uuid VARCHAR(36) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """);
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_team_members (
                        team_id VARCHAR(32) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                        last_known_name VARCHAR(32) NOT NULL,
                        role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
                        joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (team_id) REFERENCES dana_teams(id) ON DELETE CASCADE
                    );
                """);
                statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_team_members_team_id
                    ON dana_team_members (team_id);
                """);
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_team_scores (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        team_id VARCHAR(32) NOT NULL,
                        event_type VARCHAR(32) NOT NULL,
                        score_value DOUBLE NOT NULL,
                        period_month VARCHAR(7) NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """);
                statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_team_scores_composite
                    ON dana_team_scores (team_id, event_type, period_month);
                """);
            } else {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_teams (
                        id VARCHAR(32) PRIMARY KEY,
                        display_name VARCHAR(32) NOT NULL,
                        color VARCHAR(16) NOT NULL UNIQUE,
                        leader_uuid VARCHAR(36) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """);
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_team_members (
                        team_id VARCHAR(32) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                        last_known_name VARCHAR(32) NOT NULL,
                        role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
                        joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_team_members_team_id (team_id),
                        FOREIGN KEY (team_id) REFERENCES dana_teams(id) ON DELETE CASCADE
                    );
                """);
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dana_team_scores (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        team_id VARCHAR(32) NOT NULL,
                        event_type VARCHAR(32) NOT NULL,
                        score_value DOUBLE NOT NULL,
                        period_month VARCHAR(7) NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        INDEX idx_team_scores_composite (team_id, event_type, period_month)
                    );
                """);
            }
        }
    }

    /**
     * Asynchronously inserts a new team and its members into the database.
     *
     * @param team team to insert
     * @return CompletableFuture completing when inserted
     */
    public CompletableFuture<Void> insertTeam(@NotNull DanaTeam team) {
        Objects.requireNonNull(team, "team cannot be null");

        return CompletableFuture.runAsync(() -> {
            String insertTeamSql = """
                INSERT INTO dana_teams (id, display_name, color, leader_uuid, created_at)
                VALUES (?, ?, ?, ?, ?);
            """;
            String insertMemberSql;
            if (databaseManager.getConfig().type() == StorageType.SQLITE) {
                insertMemberSql = """
                    INSERT INTO dana_team_members (team_id, player_uuid, last_known_name, role, joined_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET team_id = excluded.team_id, role = excluded.role, last_known_name = excluded.last_known_name;
                """;
            } else {
                insertMemberSql = """
                    INSERT INTO dana_team_members (team_id, player_uuid, last_known_name, role, joined_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE team_id = VALUES(team_id), role = VALUES(role), last_known_name = VALUES(last_known_name);
                """;
            }

            try (Connection connection = databaseManager.getDataSource().getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement teamStmt = connection.prepareStatement(insertTeamSql)) {
                        teamStmt.setString(1, team.getId());
                        teamStmt.setString(2, team.getDisplayName());
                        teamStmt.setString(3, team.getColor().name());
                        teamStmt.setString(4, team.getLeaderUuid().toString());
                        teamStmt.setTimestamp(5, Timestamp.from(team.getCreatedAt()));
                        teamStmt.executeUpdate();
                    }

                    if (!team.getMembers().isEmpty()) {
                        try (PreparedStatement memberStmt = connection.prepareStatement(insertMemberSql)) {
                            for (TeamMember member : team.getMembers().values()) {
                                memberStmt.setString(1, team.getId());
                                memberStmt.setString(2, member.playerUuid().toString());
                                memberStmt.setString(3, member.lastKnownName());
                                memberStmt.setString(4, member.role().name());
                                memberStmt.setTimestamp(5, Timestamp.from(member.joinedAt()));
                                memberStmt.addBatch();
                            }
                            memberStmt.executeBatch();
                        }
                    }

                    connection.commit();
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Asynchronously updates a team's metadata (display name, color, leader UUID).
     *
     * @param team team with updated properties
     * @return CompletableFuture completing when updated
     */
    public CompletableFuture<Void> updateTeam(@NotNull DanaTeam team) {
        Objects.requireNonNull(team, "team cannot be null");

        return CompletableFuture.runAsync(() -> {
            String sql = """
                UPDATE dana_teams
                SET display_name = ?, color = ?, leader_uuid = ?
                WHERE id = ?;
            """;

            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, team.getDisplayName());
                statement.setString(2, team.getColor().name());
                statement.setString(3, team.getLeaderUuid().toString());
                statement.setString(4, team.getId());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Asynchronously deletes a team and all its memberships from the database.
     *
     * @param teamId team identifier
     * @return CompletableFuture completing when deleted
     */
    public CompletableFuture<Void> deleteTeam(@NotNull String teamId) {
        Objects.requireNonNull(teamId, "teamId cannot be null");

        return CompletableFuture.runAsync(() -> {
            String deleteMembersSql = "DELETE FROM dana_team_members WHERE team_id = ?;";
            String deleteTeamSql = "DELETE FROM dana_teams WHERE id = ?;";

            try (Connection connection = databaseManager.getDataSource().getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement stmt = connection.prepareStatement(deleteMembersSql)) {
                        stmt.setString(1, teamId);
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = connection.prepareStatement(deleteTeamSql)) {
                        stmt.setString(1, teamId);
                        stmt.executeUpdate();
                    }
                    connection.commit();
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Asynchronously loads all teams and their respective members from the database.
     *
     * @return CompletableFuture containing list of all teams
     */
    public CompletableFuture<List<DanaTeam>> loadAllTeams() {
        return CompletableFuture.supplyAsync(() -> {
            String selectTeamsSql = "SELECT id, display_name, color, leader_uuid, created_at FROM dana_teams;";
            String selectMembersSql = "SELECT team_id, player_uuid, last_known_name, role, joined_at FROM dana_team_members;";

            Map<String, DanaTeam> teams = new HashMap<>();
            try (Connection connection = databaseManager.getDataSource().getConnection()) {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(selectTeamsSql)) {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        String displayName = rs.getString("display_name");
                        String colorStr = rs.getString("color");
                        TeamColor color = TeamColor.fromString(colorStr).orElse(TeamColor.WHITE);
                        UUID leaderUuid = UUID.fromString(rs.getString("leader_uuid"));
                        Instant createdAt = rs.getTimestamp("created_at").toInstant();

                        DanaTeam team = new DanaTeam(id, displayName, color, leaderUuid, null, createdAt);
                        teams.put(id, team);
                    }
                }

                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(selectMembersSql)) {
                    while (rs.next()) {
                        String teamId = rs.getString("team_id");
                        DanaTeam team = teams.get(teamId);
                        if (team != null) {
                            UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                            String lastKnownName = rs.getString("last_known_name");
                            String roleStr = rs.getString("role");
                            TeamRole role = TeamRole.fromString(roleStr).orElse(TeamRole.MEMBER);
                            Instant joinedAt = rs.getTimestamp("joined_at").toInstant();

                            team.addMember(new TeamMember(playerUuid, lastKnownName, role, joinedAt));
                        }
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }

            return new ArrayList<>(teams.values());
        }, asyncExecutor);
    }

    /**
     * Asynchronously finds a team by its unique ID.
     *
     * @param teamId team identifier
     * @return CompletableFuture containing Optional DanaTeam
     */
    public CompletableFuture<Optional<DanaTeam>> findTeamById(@NotNull String teamId) {
        Objects.requireNonNull(teamId, "teamId cannot be null");

        return CompletableFuture.supplyAsync(() -> {
            String teamSql = "SELECT id, display_name, color, leader_uuid, created_at FROM dana_teams WHERE id = ?;";
            String membersSql = "SELECT player_uuid, last_known_name, role, joined_at FROM dana_team_members WHERE team_id = ?;";

            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement teamStmt = connection.prepareStatement(teamSql)) {

                teamStmt.setString(1, teamId.trim().toLowerCase());
                try (ResultSet rs = teamStmt.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    String id = rs.getString("id");
                    String displayName = rs.getString("display_name");
                    TeamColor color = TeamColor.fromString(rs.getString("color")).orElse(TeamColor.WHITE);
                    UUID leaderUuid = UUID.fromString(rs.getString("leader_uuid"));
                    Instant createdAt = rs.getTimestamp("created_at").toInstant();

                    DanaTeam team = new DanaTeam(id, displayName, color, leaderUuid, null, createdAt);

                    try (PreparedStatement memStmt = connection.prepareStatement(membersSql)) {
                        memStmt.setString(1, id);
                        try (ResultSet memRs = memStmt.executeQuery()) {
                            while (memRs.next()) {
                                UUID playerUuid = UUID.fromString(memRs.getString("player_uuid"));
                                String name = memRs.getString("last_known_name");
                                TeamRole role = TeamRole.fromString(memRs.getString("role")).orElse(TeamRole.MEMBER);
                                Instant joinedAt = memRs.getTimestamp("joined_at").toInstant();

                                team.addMember(new TeamMember(playerUuid, name, role, joinedAt));
                            }
                        }
                    }

                    return Optional.of(team);
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Asynchronously finds a team by a member's player UUID.
     *
     * @param playerUuid UUID of the player
     * @return CompletableFuture containing Optional DanaTeam
     */
    public CompletableFuture<Optional<DanaTeam>> findTeamByPlayer(@NotNull UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");

        return CompletableFuture.supplyAsync(() -> {
            String lookupSql = "SELECT team_id FROM dana_team_members WHERE player_uuid = ?;";
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(lookupSql)) {

                statement.setString(1, playerUuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        String teamId = rs.getString("team_id");
                        return findTeamById(teamId).join();
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return Optional.empty();
        }, asyncExecutor);
    }

    /**
     * Asynchronously adds or updates a member in a team.
     *
     * @param teamId target team ID
     * @param member team member record
     * @return CompletableFuture completing when persisted
     */
    public CompletableFuture<Void> addMember(@NotNull String teamId, @NotNull TeamMember member) {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(member, "member cannot be null");

        return CompletableFuture.runAsync(() -> {
            String sql;
            if (databaseManager.getConfig().type() == StorageType.SQLITE) {
                sql = """
                    INSERT INTO dana_team_members (team_id, player_uuid, last_known_name, role, joined_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET team_id = excluded.team_id, last_known_name = excluded.last_known_name, role = excluded.role;
                """;
            } else {
                sql = """
                    INSERT INTO dana_team_members (team_id, player_uuid, last_known_name, role, joined_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE team_id = VALUES(team_id), last_known_name = VALUES(last_known_name), role = VALUES(role);
                """;
            }

            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, teamId);
                statement.setString(2, member.playerUuid().toString());
                statement.setString(3, member.lastKnownName());
                statement.setString(4, member.role().name());
                statement.setTimestamp(5, Timestamp.from(member.joinedAt()));
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Asynchronously removes a player from team memberships.
     *
     * @param playerUuid UUID of the player to remove
     * @return CompletableFuture completing when deleted
     */
    public CompletableFuture<Void> removeMember(@NotNull UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");

        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM dana_team_members WHERE player_uuid = ?;";
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, playerUuid.toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Asynchronously records or updates an event score for a team.
     *
     * @param teamId team identifier
     * @param eventType type of event (e.g. 'boatrace')
     * @param scoreValue numerical score or points
     * @param periodMonth period key (e.g. '2026-09')
     * @return CompletableFuture completing when inserted
     */
    public CompletableFuture<Void> recordScore(
        @NotNull String teamId,
        @NotNull String eventType,
        double scoreValue,
        @NotNull String periodMonth
    ) {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(periodMonth, "periodMonth cannot be null");

        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO dana_team_scores (team_id, event_type, score_value, period_month, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP);
            """;

            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, teamId);
                statement.setString(2, eventType);
                statement.setDouble(3, scoreValue);
                statement.setString(4, periodMonth);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Asynchronously retrieves top team scores for an event and period, ordered descending.
     *
     * @param eventType event type identifier
     * @param periodMonth period month key
     * @param limit maximum results to return
     * @return CompletableFuture containing ordered list of TeamScoreEntry
     */
    public CompletableFuture<List<TeamScoreEntry>> getTopScores(
        @NotNull String eventType,
        @NotNull String periodMonth,
        int limit
    ) {
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(periodMonth, "periodMonth cannot be null");
        int effectiveLimit = Math.max(1, limit);

        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT id, team_id, event_type, score_value, period_month, updated_at
                FROM dana_team_scores
                WHERE event_type = ? AND period_month = ?
                ORDER BY score_value DESC
                LIMIT ?;
            """;

            List<TeamScoreEntry> results = new ArrayList<>();
            try (Connection connection = databaseManager.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, eventType);
                statement.setString(2, periodMonth);
                statement.setInt(3, effectiveLimit);

                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        String teamId = rs.getString("team_id");
                        String evType = rs.getString("event_type");
                        double score = rs.getDouble("score_value");
                        String period = rs.getString("period_month");
                        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();

                        results.add(new TeamScoreEntry(id, teamId, evType, score, period, updatedAt));
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return results;
        }, asyncExecutor);
    }
}
