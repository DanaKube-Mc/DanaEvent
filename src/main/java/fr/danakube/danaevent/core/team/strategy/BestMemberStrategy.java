package fr.danakube.danaevent.core.team.strategy;

import fr.danakube.danaevent.core.team.model.DanaTeam;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Scoring strategy retaining solely the best representative score or time among team members.
 * Supports both MAX mode (e.g. highest points/kills) and MIN mode (e.g. lowest lap time).
 */
public class BestMemberStrategy implements TeamScoringStrategy {

    public static final String ID = "best_member";

    private final boolean higherIsBetter;

    public BestMemberStrategy(boolean higherIsBetter) {
        this.higherIsBetter = higherIsBetter;
    }

    public BestMemberStrategy() {
        this(true);
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public @NotNull String getName() {
        return "Meilleur Membre";
    }

    public boolean isHigherIsBetter() {
        return higherIsBetter;
    }

    @Override
    public double calculateScore(@NotNull DanaTeam team, @NotNull List<Double> memberScores) {
        Objects.requireNonNull(team, "team cannot be null");
        Objects.requireNonNull(memberScores, "memberScores cannot be null");

        if (memberScores.isEmpty()) {
            return 0.0;
        }

        double best = higherIsBetter ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        boolean found = false;

        for (Double score : memberScores) {
            if (score != null) {
                found = true;
                if (higherIsBetter) {
                    if (score > best) {
                        best = score;
                    }
                } else {
                    if (score < best) {
                        best = score;
                    }
                }
            }
        }

        return found ? best : 0.0;
    }
}
