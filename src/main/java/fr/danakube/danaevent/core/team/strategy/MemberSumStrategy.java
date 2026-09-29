package fr.danakube.danaevent.core.team.strategy;

import fr.danakube.danaevent.core.team.model.DanaTeam;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Scoring strategy summing the individual scores of all participating team members.
 */
public class MemberSumStrategy implements TeamScoringStrategy {

    public static final String ID = "member_sum";

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public @NotNull String getName() {
        return "Somme des Membres";
    }

    @Override
    public double calculateScore(@NotNull DanaTeam team, @NotNull List<Double> memberScores) {
        Objects.requireNonNull(team, "team cannot be null");
        Objects.requireNonNull(memberScores, "memberScores cannot be null");

        double total = 0.0;
        for (Double score : memberScores) {
            if (score != null) {
                total += score;
            }
        }
        return total;
    }
}
