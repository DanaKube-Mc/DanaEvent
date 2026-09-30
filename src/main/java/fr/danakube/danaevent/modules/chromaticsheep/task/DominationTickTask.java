package fr.danakube.danaevent.modules.chromaticsheep.task;

import fr.danakube.danaevent.modules.chromaticsheep.manager.HerdManager;
import fr.danakube.danaevent.modules.chromaticsheep.manager.SheepGameManager;
import fr.danakube.danaevent.modules.chromaticsheep.manager.SheepScoreManager;
import fr.danakube.danaevent.modules.chromaticsheep.model.PlayerSheepSession;
import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepData;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepGame;
import fr.danakube.danaevent.modules.chromaticsheep.model.SpecialSheepType;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Sheep;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Periodically awards domination points per sheep to active holders during DOMINATION_TICK matches.
 */
public class DominationTickTask implements Runnable {

    private final SheepGameManager gameManager;
    private final HerdManager herdManager;
    private final SheepScoreManager scoreManager;
    private final String arenaId;

    public DominationTickTask(
        @NotNull SheepGameManager gameManager,
        @NotNull HerdManager herdManager,
        @NotNull SheepScoreManager scoreManager,
        @NotNull String arenaId
    ) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager cannot be null");
        this.herdManager = Objects.requireNonNull(herdManager, "herdManager cannot be null");
        this.scoreManager = Objects.requireNonNull(scoreManager, "scoreManager cannot be null");
        this.arenaId = Objects.requireNonNull(arenaId, "arenaId cannot be null");
    }

    @Override
    public void run() {
        SheepGame game = gameManager.getGame(arenaId);
        if (game == null || game.getArena().getScoringMode() != ScoringMode.DOMINATION_TICK) {
            return;
        }

        // Map DyeColor to effective holder UUID
        Map<DyeColor, UUID> colorToHolder = new HashMap<>();
        for (PlayerSheepSession session : game.getSessions().values()) {
            colorToHolder.put(session.getColor(), session.getEffectiveHolderUuid());
        }

        Set<UUID> sheepUuids = herdManager.getSheep(arenaId);
        if (sheepUuids.isEmpty()) {
            return;
        }

        Map<UUID, Integer> regularCounts = new HashMap<>();
        Map<UUID, Integer> goldenCounts = new HashMap<>();

        for (UUID sheepUuid : sheepUuids) {
            Entity entity = Bukkit.getEntity(sheepUuid);
            if (entity instanceof Sheep sheep && sheep.isValid() && sheep.getColor() != null) {
                UUID holder = colorToHolder.get(sheep.getColor());
                if (holder != null) {
                    SpecialSheepType type = SheepData.getSpecialType(sheep);
                    if (type == SpecialSheepType.GOLDEN) {
                        goldenCounts.merge(holder, 1, Integer::sum);
                    } else {
                        regularCounts.merge(holder, 1, Integer::sum);
                    }
                }
            }
        }

        scoreManager.handleDominationTick(arenaId, regularCounts, goldenCounts);
    }
}
