package fr.danakube.danaevent.core.team.strategy;

import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TeamScoringStrategyTest {

    private DanaTeam testTeam;

    @BeforeEach
    void setUp() {
        testTeam = new DanaTeam("champions", "Champions", TeamColor.YELLOW, UUID.randomUUID());
    }

    @Test
    @DisplayName("PlacementPointsStrategy: Should award default points based on placement ranks")
    void testPlacementPointsDefault() {
        PlacementPointsStrategy strategy = new PlacementPointsStrategy();

        assertThat(strategy.getId()).isEqualTo("placement_points");
        assertThat(strategy.getPointsForPlacement(1)).isEqualTo(100.0);
        assertThat(strategy.getPointsForPlacement(2)).isEqualTo(70.0);
        assertThat(strategy.getPointsForPlacement(3)).isEqualTo(50.0);
        assertThat(strategy.getPointsForPlacement(4)).isEqualTo(40.0);
        assertThat(strategy.getPointsForPlacement(5)).isEqualTo(30.0);
        assertThat(strategy.getPointsForPlacement(11)).isEqualTo(0.0);

        // Member scores are ranks: Member 1 came in 1st, Member 2 in 3rd
        double score = strategy.calculateScore(testTeam, List.of(1.0, 3.0));
        assertThat(score).isEqualTo(150.0);
    }

    @Test
    @DisplayName("PlacementPointsStrategy: Should accept custom points mapping")
    void testPlacementPointsCustom() {
        PlacementPointsStrategy strategy = new PlacementPointsStrategy(Map.of(
            1, 25.0,
            2, 18.0,
            3, 15.0
        ));

        assertThat(strategy.getPointsForPlacement(1)).isEqualTo(25.0);
        assertThat(strategy.getPointsForPlacement(2)).isEqualTo(18.0);
        assertThat(strategy.getPointsForPlacement(4)).isEqualTo(0.0);

        double score = strategy.calculateScore(testTeam, List.of(1.0, 2.0));
        assertThat(score).isEqualTo(43.0);
    }

    @Test
    @DisplayName("MemberSumStrategy: Should sum member scores accurately")
    void testMemberSumStrategy() {
        MemberSumStrategy strategy = new MemberSumStrategy();

        assertThat(strategy.getId()).isEqualTo("member_sum");
        assertThat(strategy.calculateScore(testTeam, List.of())).isEqualTo(0.0);

        double total = strategy.calculateScore(testTeam, List.of(15.5, 24.5, 10.0));
        assertThat(total).isEqualTo(50.0);
    }

    @Test
    @DisplayName("MemberAverageStrategy: Should compute arithmetic mean of member scores")
    void testMemberAverageStrategy() {
        MemberAverageStrategy strategy = new MemberAverageStrategy();

        assertThat(strategy.getId()).isEqualTo("member_average");
        assertThat(strategy.calculateScore(testTeam, List.of())).isEqualTo(0.0);

        double avg = strategy.calculateScore(testTeam, List.of(10.0, 20.0, 30.0));
        assertThat(avg).isCloseTo(20.0, within(0.001));

        // Boat race lap times in seconds: 45.0, 50.0
        double raceAvg = strategy.calculateScore(testTeam, List.of(45.0, 50.0));
        assertThat(raceAvg).isCloseTo(47.5, within(0.001));
    }

    @Test
    @DisplayName("BestMemberStrategy: Should select highest score when higherIsBetter is true")
    void testBestMemberStrategyMax() {
        BestMemberStrategy strategy = new BestMemberStrategy(true);

        assertThat(strategy.getId()).isEqualTo("best_member");
        assertThat(strategy.isHigherIsBetter()).isTrue();
        assertThat(strategy.calculateScore(testTeam, List.of())).isEqualTo(0.0);

        double best = strategy.calculateScore(testTeam, List.of(12.0, 85.0, 42.0));
        assertThat(best).isEqualTo(85.0);
    }

    @Test
    @DisplayName("BestMemberStrategy: Should select lowest value when higherIsBetter is false (e.g. race times)")
    void testBestMemberStrategyMin() {
        BestMemberStrategy strategy = new BestMemberStrategy(false);

        assertThat(strategy.isHigherIsBetter()).isFalse();
        assertThat(strategy.calculateScore(testTeam, List.of())).isEqualTo(0.0);

        // Lap times in milliseconds: 42100ms, 39500ms, 45000ms -> best is 39500ms
        double bestTime = strategy.calculateScore(testTeam, List.of(42100.0, 39500.0, 45000.0));
        assertThat(bestTime).isEqualTo(39500.0);
    }
}
