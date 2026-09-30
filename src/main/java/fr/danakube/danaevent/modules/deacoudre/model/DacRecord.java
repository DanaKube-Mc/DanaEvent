package fr.danakube.danaevent.modules.deacoudre.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a persistent record / statistic entry for a player or team in Dé à Coudre.
 */
public record DacRecord(
    long id,
    @NotNull String arenaId,
    @NotNull UUID holderUuid,
    boolean isTeam,
    int wins,
    int successfulJumps,
    int perfectDacs,
    @NotNull String periodMonth,
    @NotNull Instant updatedAt
) {
    public DacRecord {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");
        Objects.requireNonNull(periodMonth, "periodMonth cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    public DacRecord(
        @NotNull String arenaId,
        @NotNull UUID holderUuid,
        boolean isTeam,
        int wins,
        int successfulJumps,
        int perfectDacs,
        @NotNull String periodMonth,
        @NotNull Instant updatedAt
    ) {
        this(0L, arenaId, holderUuid, isTeam, wins, successfulJumps, perfectDacs, periodMonth, updatedAt);
    }
}
