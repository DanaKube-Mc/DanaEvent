package fr.danakube.danaevent.modules.treasurehunt.listener;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.modules.treasurehunt.config.HuntConfig;
import fr.danakube.danaevent.modules.treasurehunt.manager.HuntProgressManager;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntStep;
import fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress;
import fr.danakube.danaevent.modules.treasurehunt.model.StepTriggerType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Listens for block interactions (chests, heads, signs) to trigger BLOCK_CLICK hunt objectives.
 * Implements strict sequence validation and anti-sheep error messaging.
 */
public class HuntBlockInteractListener implements Listener {

    private final DanaEventPlugin plugin;
    private final HuntProgressManager progressManager;
    private final HuntConfig huntConfig;

    public HuntBlockInteractListener(
        @NotNull DanaEventPlugin plugin,
        @NotNull HuntProgressManager progressManager,
        @NotNull HuntConfig huntConfig
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.progressManager = Objects.requireNonNull(progressManager, "progressManager cannot be null");
        this.huntConfig = Objects.requireNonNull(huntConfig, "huntConfig cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        Player player = event.getPlayer();
        Optional<PlayerHuntProgress> progressOpt = progressManager.getProgressForPlayer(player);

        if (progressOpt.isPresent()) {
            PlayerHuntProgress progress = progressOpt.get();
            Optional<Hunt> huntOpt = huntConfig.getHunt(progress.getHuntId());
            if (huntOpt.isEmpty() || !huntOpt.get().isEnabled()) {
                return;
            }

            Hunt hunt = huntOpt.get();
            int activeStepNum = progress.getActiveStepNumber();
            Optional<HuntStep> activeStepOpt = hunt.getStep(activeStepNum);

            // 1. Check if clicked block matches active step
            if (activeStepOpt.isPresent() && activeStepOpt.get().matchesBlockLocation(clickedBlock.getLocation())) {
                event.setCancelled(true);
                progressManager.validateStepForPlayer(player, activeStepNum);
                return;
            }

            // 2. Check if clicked block matches any other step in this hunt
            boolean matchesOtherHuntStep = hunt.getSteps().stream()
                .filter(step -> step.getTriggerType() == StepTriggerType.BLOCK_CLICK)
                .anyMatch(step -> step.matchesBlockLocation(clickedBlock.getLocation()));

            if (matchesOtherHuntStep) {
                event.setCancelled(true);
                plugin.getMessageManager().sendMessage(player, "hunt-wrong-step");
                return;
            }

            // 3. Check if clicked block belongs to any other active hunt
            for (Hunt otherHunt : huntConfig.getHunts()) {
                if (!otherHunt.isEnabled() || otherHunt.getId().equals(hunt.getId())) {
                    continue;
                }
                boolean matches = otherHunt.getSteps().stream()
                    .filter(step -> step.getTriggerType() == StepTriggerType.BLOCK_CLICK)
                    .anyMatch(step -> step.matchesBlockLocation(clickedBlock.getLocation()));
                if (matches) {
                    event.setCancelled(true);
                    plugin.getMessageManager().sendMessage(player, "hunt-wrong-step");
                    return;
                }
            }
        } else {
            // Player is not in any hunt: check if block belongs to any enabled hunt
            for (Hunt hunt : huntConfig.getHunts()) {
                if (!hunt.isEnabled()) {
                    continue;
                }
                boolean matches = hunt.getSteps().stream()
                    .filter(step -> step.getTriggerType() == StepTriggerType.BLOCK_CLICK)
                    .anyMatch(step -> step.matchesBlockLocation(clickedBlock.getLocation()));
                if (matches) {
                    event.setCancelled(true);
                    plugin.getMessageManager().sendMessage(player, "hunt-wrong-step");
                    return;
                }
            }
        }
    }
}
