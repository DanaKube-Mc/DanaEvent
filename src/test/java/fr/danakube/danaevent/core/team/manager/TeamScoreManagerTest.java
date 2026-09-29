package fr.danakube.danaevent.core.team.manager;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.team.database.TeamDatabase;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import fr.danakube.danaevent.core.team.model.TeamScoreEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamScoreManagerTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private TeamDatabase teamDatabase;
    private TeamScoreManager scoreManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        teamDatabase = plugin.getTeamDatabase();
        scoreManager = plugin.getTeamScoreManager();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should initialize with all 4 default strategies registered")
    void shouldInitializeWithDefaultStrategies() {
        assertThat(scoreManager.getStrategies()).hasSize(4);
        assertThat(scoreManager.getStrategy("placement_points")).isPresent();
        assertThat(scoreManager.getStrategy("member_sum")).isPresent();
        assertThat(scoreManager.getStrategy("member_average")).isPresent();
        assertThat(scoreManager.getStrategy("best_member")).isPresent();
    }

    @Test
    @DisplayName("Should format current period month as YYYY-MM")
    void shouldFormatCurrentPeriodMonth() {
        String period = scoreManager.getCurrentPeriodMonth();
        assertThat(period).matches("^\\d{4}-\\d{2}$");
    }

    @Test
    @DisplayName("Should compute and record score with designated strategy")
    void shouldComputeAndRecordScoreWithStrategy() {
        DanaTeam team = new DanaTeam("tigers", "Tigers", TeamColor.ORANGE, UUID.randomUUID());
        teamDatabase.insertTeam(team).join();

        // Member placements: 1st (100 pts) and 2nd (70 pts) -> 170 pts
        Double computed = scoreManager.computeAndRecordTeamScore(
            team,
            "boatrace",
            "placement_points",
            List.of(1.0, 2.0),
            "2026-09"
        ).join();

        assertThat(computed).isEqualTo(170.0);

        Double total = scoreManager.getTeamTotalScore("tigers", "boatrace", "2026-09").join();
        assertThat(total).isEqualTo(170.0);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when strategy ID is unknown")
    void shouldThrowOnUnknownStrategy() {
        DanaTeam team = new DanaTeam("lions", "Lions", TeamColor.YELLOW, UUID.randomUUID());

        assertThatThrownBy(() -> scoreManager.computeAndRecordTeamScore(
            team,
            "boatrace",
            "non_existent_strategy",
            List.of(1.0),
            "2026-09"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown scoring strategy");
    }

    @Test
    @DisplayName("Should retrieve leaderboard sorted by score descending")
    void shouldRetrieveLeaderboardSortedDescending() {
        DanaTeam teamA = new DanaTeam("team_a", "Team A", TeamColor.RED, UUID.randomUUID());
        DanaTeam teamB = new DanaTeam("team_b", "Team B", TeamColor.BLUE, UUID.randomUUID());
        teamDatabase.insertTeam(teamA).join();
        teamDatabase.insertTeam(teamB).join();

        scoreManager.recordScore("team_a", "boatrace", 50.0, "2026-09").join();
        scoreManager.recordScore("team_b", "boatrace", 120.0, "2026-09").join();

        List<TeamScoreEntry> leaderboard = scoreManager.getLeaderboard("boatrace", "2026-09", 10).join();

        assertThat(leaderboard).hasSize(2);
        assertThat(leaderboard.get(0).teamId()).isEqualTo("team_b");
        assertThat(leaderboard.get(0).scoreValue()).isEqualTo(120.0);
        assertThat(leaderboard.get(1).teamId()).isEqualTo("team_a");
        assertThat(leaderboard.get(1).scoreValue()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Should reset scores for a given event and period")
    void shouldResetScores() {
        DanaTeam team = new DanaTeam("hawks", "Hawks", TeamColor.CYAN, UUID.randomUUID());
        teamDatabase.insertTeam(team).join();

        scoreManager.recordScore("hawks", "boatrace", 75.0, "2026-09").join();
        assertThat(scoreManager.getTeamTotalScore("hawks", "boatrace", "2026-09").join()).isEqualTo(75.0);

        int deleted = scoreManager.resetScores("boatrace", "2026-09").join();
        assertThat(deleted).isGreaterThanOrEqualTo(1);

        assertThat(scoreManager.getTeamTotalScore("hawks", "boatrace", "2026-09").join()).isEqualTo(0.0);
    }
}
