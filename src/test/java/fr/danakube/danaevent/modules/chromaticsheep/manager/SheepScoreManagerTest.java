package fr.danakube.danaevent.modules.chromaticsheep.manager;

import fr.danakube.danaevent.modules.chromaticsheep.model.SpecialSheepType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SheepScoreManagerTest {

    private SheepScoreManager scoreManager;
    private final String arenaId = "arena_score";

    @BeforeEach
    void setUp() {
        scoreManager = new SheepScoreManager();
    }

    @Test
    @DisplayName("Should compute action score points properly with neutral, steal and golden multiplier")
    void shouldComputeActionScores() {
        UUID player1 = UUID.randomUUID();

        // Neutral dye = 1 pt
        int score1 = scoreManager.handleActionDye(arenaId, player1, true, false, SpecialSheepType.NORMAL);
        assertThat(score1).isEqualTo(1);

        // Steal dye = 3 pts -> total 4 pts
        int score2 = scoreManager.handleActionDye(arenaId, player1, false, true, SpecialSheepType.NORMAL);
        assertThat(score2).isEqualTo(4);

        // Steal golden sheep = 3 * 5 = 15 pts -> total 19 pts
        int score3 = scoreManager.handleActionDye(arenaId, player1, false, true, SpecialSheepType.GOLDEN);
        assertThat(score3).isEqualTo(19);

        // Neutral golden sheep = 1 * 5 = 5 pts -> total 24 pts
        int score4 = scoreManager.handleActionDye(arenaId, player1, true, false, SpecialSheepType.GOLDEN);
        assertThat(score4).isEqualTo(24);
    }

    @Test
    @DisplayName("Should award bomb impact points (+2 pts per sheep)")
    void shouldAwardBombPoints() {
        UUID player = UUID.randomUUID();

        int score = scoreManager.handleBombImpact(arenaId, player, 4);
        assertThat(score).isEqualTo(8);

        // 0 sheep hit should not increase score
        int unchanged = scoreManager.handleBombImpact(arenaId, player, 0);
        assertThat(unchanged).isEqualTo(8);
    }

    @Test
    @DisplayName("Should award domination tick points (+1 regular, +5 golden)")
    void shouldAwardDominationTickPoints() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        Map<UUID, Integer> regular = Map.of(p1, 5, p2, 10);
        Map<UUID, Integer> golden = Map.of(p1, 2, p2, 0);

        scoreManager.handleDominationTick(arenaId, regular, golden);

        // P1: 5 regular (5 pts) + 2 golden (10 pts) = 15 pts
        assertThat(scoreManager.getScore(arenaId, p1)).isEqualTo(15);

        // P2: 10 regular (10 pts) + 0 golden = 10 pts
        assertThat(scoreManager.getScore(arenaId, p2)).isEqualTo(10);
    }

    @Test
    @DisplayName("Should return sorted live leaderboard descending by score")
    void shouldReturnSortedLeaderboard() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        scoreManager.setScore(arenaId, p1, 50);
        scoreManager.setScore(arenaId, p2, 120);
        scoreManager.setScore(arenaId, p3, 85);

        List<Map.Entry<UUID, Integer>> leaderboard = scoreManager.getLeaderboard(arenaId);
        assertThat(leaderboard).hasSize(3);

        assertThat(leaderboard.get(0).getKey()).isEqualTo(p2);
        assertThat(leaderboard.get(0).getValue()).isEqualTo(120);

        assertThat(leaderboard.get(1).getKey()).isEqualTo(p3);
        assertThat(leaderboard.get(1).getValue()).isEqualTo(85);

        assertThat(leaderboard.get(2).getKey()).isEqualTo(p1);
        assertThat(leaderboard.get(2).getValue()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should reset arena scores properly")
    void shouldResetArena() {
        UUID p1 = UUID.randomUUID();
        scoreManager.setScore(arenaId, p1, 100);
        assertThat(scoreManager.getScore(arenaId, p1)).isEqualTo(100);

        scoreManager.resetArena(arenaId);
        assertThat(scoreManager.getScore(arenaId, p1)).isEqualTo(0);
        assertThat(scoreManager.getLeaderboard(arenaId)).isEmpty();
    }
}
