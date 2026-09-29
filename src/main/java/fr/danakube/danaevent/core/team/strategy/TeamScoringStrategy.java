package fr.danakube.danaevent.core.team.strategy;

import fr.danakube.danaevent.core.team.model.DanaTeam;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Strategy interface for computing team scores and tournament points across various event types.
 */
public interface TeamScoringStrategy {

    /**
     * Unique identifier for this scoring strategy.
     *
     * @return lowercase string identifier
     */
    @NotNull String getId();

    /**
     * Human-readable display name for this scoring strategy.
     *
     * @return display name
     */
    @NotNull String getName();

    /**
     * Calculates the aggregate team score from a list of member scores.
     *
     * @param team         the DanaTeam being scored
     * @param memberScores list of numerical scores obtained by participating members
     * @return the calculated final team score
     */
    double calculateScore(@NotNull DanaTeam team, @NotNull List<Double> memberScores);
}
