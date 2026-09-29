package fr.danakube.danaevent.core.team.strategy;

import fr.danakube.danaevent.core.team.model.DanaTeam;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Scoring strategy computing the arithmetic mean (average) of member scores or times.
 */
public class MemberAverageStrategy implements TeamScoringStrategy {

    public static final String ID = "member_average";

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public @NotNull String getName() {
        return "Moyenne des Membres";
    }

    @Override
    public double calculateScore(@NotNull DanaTeam team, @NotNull List<Double> memberScores) {
        Objects.requireNonNull(team, "team cannot be null");
        Objects.requireNonNull(memberScores, "memberScores cannot be null");

        if (memberScores.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        int count = 0;
        for (Double score : memberScores) {
            if (score != null) {
                total += score;
                count++;
            }
        }
        return count == 0 ? 0.0 : total / count;
    }
}
