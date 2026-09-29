package fr.danakube.danaevent.core.team.strategy;

import fr.danakube.danaevent.core.team.model.DanaTeam;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Scoring strategy awarding predefined points based on tournament ranking or member placements.
 * Default distribution: 1st: 100, 2nd: 70, 3rd: 50, 4th: 40, 5th: 30, 6th: 20, 7th: 15, 8th: 10.
 */
public class PlacementPointsStrategy implements TeamScoringStrategy {

    public static final String ID = "placement_points";

    private static final Map<Integer, Double> DEFAULT_POINTS = Map.of(
        1, 100.0,
        2, 70.0,
        3, 50.0,
        4, 40.0,
        5, 30.0,
        6, 20.0,
        7, 15.0,
        8, 10.0,
        9, 5.0,
        10, 2.0
    );

    private final Map<Integer, Double> pointsTable;

    public PlacementPointsStrategy(@NotNull Map<Integer, Double> pointsTable) {
        this.pointsTable = Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNull(pointsTable, "pointsTable cannot be null")));
    }

    public PlacementPointsStrategy() {
        this(DEFAULT_POINTS);
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public @NotNull String getName() {
        return "Points de Placement";
    }

    /**
     * Retrieves the points awarded for a given placement rank.
     *
     * @param placement 1-based rank (1 = 1st place)
     * @return points awarded, or 0.0 if not ranked
     */
    public double getPointsForPlacement(int placement) {
        return pointsTable.getOrDefault(placement, 0.0);
    }

    @Override
    public double calculateScore(@NotNull DanaTeam team, @NotNull List<Double> memberScores) {
        Objects.requireNonNull(team, "team cannot be null");
        Objects.requireNonNull(memberScores, "memberScores cannot be null");

        double total = 0.0;
        for (Double rankVal : memberScores) {
            if (rankVal != null) {
                int rank = rankVal.intValue();
                total += getPointsForPlacement(rank);
            }
        }
        return total;
    }
}
