package fr.danakube.danaevent.modules.chromaticsheep.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record representing a completed ChromaticSheep game score.
 *
 * @param id database primary key identifier (0 if not persisted yet)
 * @param arenaId identifier of the arena
 * @param holderUuid UUID of the player (in solo) or team UUID (in team mode)
 * @param isTeam true if this record belongs to a team, false if solo
 * @param scorePoints points scored during the game
 * @param sheepCount number of sheep of this color at the end of the game
 * @param scoringMode scoring mode used during the game
 * @param periodMonth month of competition (e.g. "2026-09")
 * @param playedAt timestamp when the record was recorded
 */
public record SheepRecord(
    long id,
    @NotNull String arenaId,
    @NotNull UUID holderUuid,
    boolean isTeam,
    int scorePoints,
    int sheepCount,
    @NotNull ScoringMode scoringMode,
    @NotNull String periodMonth,
    @NotNull Instant playedAt
) {
    public SheepRecord {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");
        Objects.requireNonNull(scoringMode, "scoringMode cannot be null");
        Objects.requireNonNull(periodMonth, "periodMonth cannot be null");
        Objects.requireNonNull(playedAt, "playedAt cannot be null");
        arenaId = arenaId.trim().toLowerCase();
    }

    public SheepRecord(
        @NotNull String arenaId,
        @NotNull UUID holderUuid,
        boolean isTeam,
        int scorePoints,
        int sheepCount,
        @NotNull ScoringMode scoringMode,
        @NotNull String periodMonth,
        @NotNull Instant playedAt
    ) {
        this(0L, arenaId, holderUuid, isTeam, scorePoints, sheepCount, scoringMode, periodMonth, playedAt);
    }
}
