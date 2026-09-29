package fr.danakube.danaevent.core.team.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a recorded event score for a team during a specific period.
 */
public record TeamScoreEntry(
    long id,
    @NotNull String teamId,
    @NotNull String eventType,
    double scoreValue,
    @NotNull String periodMonth,
    @NotNull Instant updatedAt
) {
    public TeamScoreEntry {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(periodMonth, "periodMonth cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    public TeamScoreEntry(
        @NotNull String teamId,
        @NotNull String eventType,
        double scoreValue,
        @NotNull String periodMonth
    ) {
        this(0L, teamId, eventType, scoreValue, periodMonth, Instant.now());
    }
}
