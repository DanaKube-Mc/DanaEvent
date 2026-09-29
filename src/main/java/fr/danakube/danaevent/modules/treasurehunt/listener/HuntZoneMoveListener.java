package fr.danakube.danaevent.modules.treasurehunt.listener;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.modules.treasurehunt.config.HuntConfig;
import fr.danakube.danaevent.modules.treasurehunt.manager.HuntProgressManager;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntStep;
import fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress;
import fr.danakube.danaevent.modules.treasurehunt.model.StepTriggerType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for player movement into target cuboid regions to trigger ZONE_ENTER objectives.
 */
public class HuntZoneMoveListener implements Listener {

    private final DanaEventPlugin plugin;
    private final HuntProgressManager progressManager;
    private final HuntConfig huntConfig;
    private final Map<UUID, Long> debounceMap = new ConcurrentHashMap<>();

    public HuntZoneMoveListener(
        @NotNull DanaEventPlugin plugin,
        @NotNull HuntProgressManager progressManager,
        @NotNull HuntConfig huntConfig
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.progressManager = Objects.requireNonNull(progressManager, "progressManager cannot be null");
        this.huntConfig = Objects.requireNonNull(huntConfig, "huntConfig cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        // Only evaluate if the player moved across block boundaries
        if (from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        Optional<PlayerHuntProgress> progressOpt = progressManager.getProgressForPlayer(player);
        if (progressOpt.isEmpty()) {
            return;
        }

        PlayerHuntProgress progress = progressOpt.get();
        Optional<Hunt> huntOpt = huntConfig.getHunt(progress.getHuntId());
        if (huntOpt.isEmpty() || !huntOpt.get().isEnabled()) {
            return;
        }

        int activeStepNum = progress.getActiveStepNumber();
        Optional<HuntStep> activeStepOpt = huntOpt.get().getStep(activeStepNum);
        if (activeStepOpt.isEmpty()) {
            return;
        }

        HuntStep step = activeStepOpt.get();
        if (step.getTriggerType() != StepTriggerType.ZONE_ENTER) {
            return;
        }

        if (step.matchesRegion(to)) {
            long now = System.currentTimeMillis();
            Long last = debounceMap.get(player.getUniqueId());
            if (last != null && now - last < 2000L) {
                return;
            }
            debounceMap.put(player.getUniqueId(), now);

            progressManager.validateStepForPlayer(player, activeStepNum);
        }
    }

    public void cleanUp() {
        debounceMap.clear();
    }
}
