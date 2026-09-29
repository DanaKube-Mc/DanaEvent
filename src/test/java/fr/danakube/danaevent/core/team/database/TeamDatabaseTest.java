package fr.danakube.danaevent.core.team.database;

import fr.danakube.danaevent.core.database.DatabaseConfig;
import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import fr.danakube.danaevent.core.team.model.TeamMember;
import fr.danakube.danaevent.core.team.model.TeamRole;
import fr.danakube.danaevent.core.team.model.TeamScoreEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamDatabaseTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private TeamDatabase teamDatabase;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseConfig config = new DatabaseConfig(
            StorageType.SQLITE,
            "localhost",
            3306,
            "team_test.db",
            "",
            "",
            5
        );
        databaseManager = new DatabaseManager(config, tempDir.toFile());
        teamDatabase = new TeamDatabase(databaseManager);
        teamDatabase.initTables();
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null && databaseManager.isRunning()) {
            databaseManager.shutdown();
        }
    }

    @Test
    @DisplayName("initTables should be idempotent")
    void shouldBeIdempotentOnTableInit() throws SQLException {
        teamDatabase.initTables();
        teamDatabase.initTables();
    }

    @Test
    @DisplayName("Should insert team with members and find by ID and player UUID")
    void shouldInsertAndFindTeam() throws ExecutionException, InterruptedException {
        UUID leaderUuid = UUID.randomUUID();
        UUID memberUuid = UUID.randomUUID();

        DanaTeam team = new DanaTeam("eclairs", "Les Éclairs", TeamColor.YELLOW, leaderUuid);
        team.addMember(new TeamMember(leaderUuid, "LeaderPlayer", TeamRole.LEADER, Instant.now()));
        team.addMember(new TeamMember(memberUuid, "MemberPlayer", TeamRole.MEMBER, Instant.now()));

        teamDatabase.insertTeam(team).get();

        // 1. Find by ID
        Optional<DanaTeam> byId = teamDatabase.findTeamById("eclairs").get();
        assertThat(byId).isPresent();
        DanaTeam loaded = byId.get();
        assertThat(loaded.getId()).isEqualTo("eclairs");
        assertThat(loaded.getDisplayName()).isEqualTo("Les Éclairs");
        assertThat(loaded.getColor()).isEqualTo(TeamColor.YELLOW);
        assertThat(loaded.getLeaderUuid()).isEqualTo(leaderUuid);
        assertThat(loaded.getMemberCount()).isEqualTo(2);
        assertThat(loaded.hasMember(leaderUuid)).isTrue();
        assertThat(loaded.hasMember(memberUuid)).isTrue();
        assertThat(loaded.getMember(leaderUuid).orElseThrow().role()).isEqualTo(TeamRole.LEADER);

        // 2. Find by Player
        Optional<DanaTeam> byPlayer = teamDatabase.findTeamByPlayer(memberUuid).get();
        assertThat(byPlayer).isPresent();
        assertThat(byPlayer.get().getId()).isEqualTo("eclairs");

        // 3. Find by unknown player
        assertThat(teamDatabase.findTeamByPlayer(UUID.randomUUID()).get()).isEmpty();
    }

    @Test
    @DisplayName("Should update team metadata")
    void shouldUpdateTeamMetadata() throws ExecutionException, InterruptedException {
        UUID leader1 = UUID.randomUUID();
        UUID leader2 = UUID.randomUUID();

        DanaTeam team = new DanaTeam("loups", "Les Loups", TeamColor.GRAY, leader1);
        team.addMember(new TeamMember(leader1, "Wolf1", TeamRole.LEADER));
        team.addMember(new TeamMember(leader2, "Wolf2", TeamRole.MEMBER));
        teamDatabase.insertTeam(team).get();

        // Update name, color and leader
        team.setDisplayName("Les Grands Loups");
        team.setColor(TeamColor.BLACK);
        team.setLeader(leader2);

        teamDatabase.updateTeam(team).get();

        Optional<DanaTeam> loaded = teamDatabase.findTeamById("loups").get();
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getDisplayName()).isEqualTo("Les Grands Loups");
        assertThat(loaded.get().getColor()).isEqualTo(TeamColor.BLACK);
        assertThat(loaded.get().getLeaderUuid()).isEqualTo(leader2);
    }

    @Test
    @DisplayName("Should enforce strict color uniqueness constraint in database")
    void shouldEnforceColorUniqueness() throws ExecutionException, InterruptedException {
        UUID leader1 = UUID.randomUUID();
        UUID leader2 = UUID.randomUUID();

        DanaTeam team1 = new DanaTeam("team_red_1", "Rouge Un", TeamColor.RED, leader1);
        teamDatabase.insertTeam(team1).get();

        // Attempting to insert a second team with RED color must fail
        DanaTeam team2 = new DanaTeam("team_red_2", "Rouge Deux", TeamColor.RED, leader2);

        assertThatThrownBy(() -> teamDatabase.insertTeam(team2).join())
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("Should add, remove and list members in a team")
    void shouldAddAndRemoveMembers() throws ExecutionException, InterruptedException {
        UUID leader = UUID.randomUUID();
        UUID newMember = UUID.randomUUID();

        DanaTeam team = new DanaTeam("faucons", "Les Faucons", TeamColor.CYAN, leader);
        team.addMember(new TeamMember(leader, "FalconLeader", TeamRole.LEADER));
        teamDatabase.insertTeam(team).get();

        // Add member
        teamDatabase.addMember("faucons", new TeamMember(newMember, "FalconWing", TeamRole.MEMBER)).get();
        DanaTeam loadedAfterAdd = teamDatabase.findTeamById("faucons").get().orElseThrow();
        assertThat(loadedAfterAdd.hasMember(newMember)).isTrue();
        assertThat(loadedAfterAdd.getMemberCount()).isEqualTo(2);

        // Remove member
        teamDatabase.removeMember(newMember).get();
        DanaTeam loadedAfterRemove = teamDatabase.findTeamById("faucons").get().orElseThrow();
        assertThat(loadedAfterRemove.hasMember(newMember)).isFalse();
        assertThat(loadedAfterRemove.getMemberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should delete team and cascade member removals")
    void shouldDeleteTeamAndCascade() throws ExecutionException, InterruptedException {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();

        DanaTeam team = new DanaTeam("requins", "Les Requins", TeamColor.BLUE, leader);
        team.addMember(new TeamMember(leader, "Shark1", TeamRole.LEADER));
        team.addMember(new TeamMember(member, "Shark2", TeamRole.MEMBER));
        teamDatabase.insertTeam(team).get();

        // Delete team
        teamDatabase.deleteTeam("requins").get();

        assertThat(teamDatabase.findTeamById("requins").get()).isEmpty();
        assertThat(teamDatabase.findTeamByPlayer(member).get()).isEmpty();
        assertThat(teamDatabase.loadAllTeams().get()).isEmpty();
    }

    @Test
    @DisplayName("Should record and query top team scores ordered descending")
    void shouldRecordAndRetrieveTopScores() throws ExecutionException, InterruptedException {
        String eventType = "boatrace";
        String period = "2026-09";

        teamDatabase.recordScore("team_a", eventType, 150.0, period).get();
        teamDatabase.recordScore("team_b", eventType, 300.0, period).get();
        teamDatabase.recordScore("team_c", eventType, 220.0, period).get();
        teamDatabase.recordScore("team_other_event", "pvp", 500.0, period).get();
        teamDatabase.recordScore("team_other_period", eventType, 400.0, "2026-10").get();

        List<TeamScoreEntry> top = teamDatabase.getTopScores(eventType, period, 10).get();
        assertThat(top).hasSize(3);

        // Ordered DESC: team_b (300.0) > team_c (220.0) > team_a (150.0)
        assertThat(top.get(0).teamId()).isEqualTo("team_b");
        assertThat(top.get(0).scoreValue()).isEqualTo(300.0);

        assertThat(top.get(1).teamId()).isEqualTo("team_c");
        assertThat(top.get(1).scoreValue()).isEqualTo(220.0);

        assertThat(top.get(2).teamId()).isEqualTo("team_a");
        assertThat(top.get(2).scoreValue()).isEqualTo(150.0);
    }
}
