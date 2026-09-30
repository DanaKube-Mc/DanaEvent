package fr.danakube.danaevent.modules.chromaticsheep.manager;

import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SpecialSheepType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calculates and maintains live scores for ChromaticSheep games based on the active ScoringMode.
 */
public class SheepScoreManager {

    private final Map<String, Map<UUID, Integer>> arenaScores = new ConcurrentHashMap<>();

    /**
     * Gets current score points for a holder in an arena.
     */
    public int getScore(@NotNull String arenaId, @NotNull UUID holderUuid) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");

        Map<UUID, Integer> scores = arenaScores.get(arenaId.trim().toLowerCase());
        if (scores == null) {
            return 0;
        }
        return scores.getOrDefault(holderUuid, 0);
    }

    /**
     * Sets the score directly.
     */
    public void setScore(@NotNull String arenaId, @NotNull UUID holderUuid, int score) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");

        arenaScores.computeIfAbsent(arenaId.trim().toLowerCase(), k -> new ConcurrentHashMap<>())
            .put(holderUuid, score);
    }

    /**
     * Adds score points to a holder.
     */
    public int addScore(@NotNull String arenaId, @NotNull UUID holderUuid, int points) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");

        return arenaScores.computeIfAbsent(arenaId.trim().toLowerCase(), k -> new ConcurrentHashMap<>())
            .merge(holderUuid, points, Integer::sum);
    }

    /**
     * Computes and awards points for dyeing a sheep in ACTION_SCORE mode.
     *
     * @param isNeutral true if sheep was uncolored/white
     * @param isSteal true if sheep belonged to an opposing player/team
     * @param type special type of the sheep (e.g. GOLDEN multiplies by 5)
     */
    public int handleActionDye(
        @NotNull String arenaId,
        @NotNull UUID holderUuid,
        boolean isNeutral,
        boolean isSteal,
        @NotNull SpecialSheepType type
    ) {
        int basePoints;
        if (isSteal) {
            basePoints = 3;
        } else if (isNeutral) {
            basePoints = 1;
        } else {
            basePoints = 1;
        }

        if (type == SpecialSheepType.GOLDEN) {
            basePoints *= 5;
        }

        return addScore(arenaId, holderUuid, basePoints);
    }

    /**
     * Awards points for sheep affected by a paint bomb (+2 pts per sheep).
     */
    public int handleBombImpact(@NotNull String arenaId, @NotNull UUID holderUuid, int sheepCount) {
        if (sheepCount <= 0) {
            return getScore(arenaId, holderUuid);
        }
        int points = sheepCount * 2;
        return addScore(arenaId, holderUuid, points);
    }

    /**
     * Awards domination tick points (+1 pt per regular sheep, +5 pts for golden sheep).
     */
    public void handleDominationTick(
        @NotNull String arenaId,
        @NotNull Map<UUID, Integer> regularSheepCount,
        @NotNull Map<UUID, Integer> goldenSheepCount
    ) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");

        for (Map.Entry<UUID, Integer> entry : regularSheepCount.entrySet()) {
            int regular = entry.getValue();
            int golden = goldenSheepCount.getOrDefault(entry.getKey(), 0);
            int points = regular + (golden * 5);
            if (points > 0) {
                addScore(arenaId, entry.getKey(), points);
            }
        }
    }

    /**
     * Returns an unmodifiable snapshot of live ranking sorted descending by score.
     */
    public List<Map.Entry<UUID, Integer>> getLeaderboard(@NotNull String arenaId) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");

        Map<UUID, Integer> scores = arenaScores.get(arenaId.trim().toLowerCase());
        if (scores == null || scores.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(scores.entrySet());
        list.sort((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));
        return Collections.unmodifiableList(list);
    }

    /**
     * Resets scores for a specific arena.
     */
    public void resetArena(@NotNull String arenaId) {
        arenaScores.remove(arenaId.trim().toLowerCase());
    }

    /**
     * Cleans up all scores.
     */
    public void cleanUpAll() {
        arenaScores.clear();
    }
}
