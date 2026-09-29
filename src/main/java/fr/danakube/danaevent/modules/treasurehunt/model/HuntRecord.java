package fr.danakube.danaevent.modules.treasurehunt.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record representing a completed treasure hunt time for leaderboard ranking.
 */
public record HuntRecord(
    long id,
    @NotNull String huntId,
    @NotNull UUID holderUuid,
    boolean isTeam,
    long timeMillis,
    @NotNull String periodMonth,
    @NotNull Instant completedAt
) {

    public HuntRecord {
        Objects.requireNonNull(huntId, "huntId cannot be null");
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");
        Objects.requireNonNull(periodMonth, "periodMonth cannot be null");
        Objects.requireNonNull(completedAt, "completedAt cannot be null");
    }

    public HuntRecord(
        @NotNull String huntId,
        @NotNull UUID holderUuid,
        boolean isTeam,
        long timeMillis,
        @NotNull String periodMonth,
        @NotNull Instant completedAt
    ) {
        this(0L, huntId, holderUuid, isTeam, timeMillis, periodMonth, completedAt);
    }

    public String formatTime() {
        return PlayerHuntProgress.formatTime(timeMillis);
    }
}
