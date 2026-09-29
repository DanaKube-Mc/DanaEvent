package fr.danakube.danaevent.modules.treasurehunt.display;

import fr.danakube.danaevent.modules.treasurehunt.config.HuntConfig;
import fr.danakube.danaevent.modules.treasurehunt.manager.HuntProgressManager;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntStep;
import fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress;
import fr.danakube.danaevent.modules.treasurehunt.model.StepTriggerType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Periodically displays discreet particles above active objective targets, visible ONLY to the
 * player or team who has reached that exact step.
 */
public class ClueParticleTask extends BukkitRunnable {

    private static final double MAX_DISTANCE_SQUARED = 32.0 * 32.0;

    private final HuntProgressManager progressManager;
    private final HuntConfig huntConfig;

    public ClueParticleTask(@NotNull HuntProgressManager progressManager, @NotNull HuntConfig huntConfig) {
        this.progressManager = Objects.requireNonNull(progressManager, "progressManager cannot be null");
        this.huntConfig = Objects.requireNonNull(huntConfig, "huntConfig cannot be null");
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Optional<PlayerHuntProgress> progressOpt = progressManager.getProgressForPlayer(player);
            if (progressOpt.isEmpty()) {
                continue;
            }

            PlayerHuntProgress progress = progressOpt.get();
            Optional<Hunt> huntOpt = huntConfig.getHunt(progress.getHuntId());
            if (huntOpt.isEmpty() || !huntOpt.get().isEnabled()) {
                continue;
            }

            Hunt hunt = huntOpt.get();
            int activeStepNum = progress.getActiveStepNumber();
            Optional<HuntStep> stepOpt = hunt.getStep(activeStepNum);
            if (stepOpt.isEmpty()) {
                continue;
            }

            HuntStep step = stepOpt.get();
            if (step.getTriggerType() == StepTriggerType.BLOCK_CLICK && step.getTargetLocation() != null) {
                Location target = step.getTargetLocation();
                if (target.getWorld() != null && target.getWorld().equals(player.getWorld())) {
                    if (player.getLocation().distanceSquared(target) <= MAX_DISTANCE_SQUARED) {
                        player.spawnParticle(
                            Particle.HAPPY_VILLAGER,
                            target.clone().add(0.5, 1.2, 0.5),
                            3,
                            0.2,
                            0.2,
                            0.2,
                            0.0
                        );
                    }
                }
            } else if (step.getTriggerType() == StepTriggerType.ZONE_ENTER && step.getTargetRegion() != null) {
                Location center = step.getTargetRegion().getCenter(player.getWorld());
                if (center != null && center.getWorld() != null && center.getWorld().equals(player.getWorld())) {
                    if (player.getLocation().distanceSquared(center) <= MAX_DISTANCE_SQUARED) {
                        player.spawnParticle(
                            Particle.PORTAL,
                            center,
                            5,
                            0.5,
                            0.5,
                            0.5,
                            0.0
                        );
                    }
                }
            }
        }
    }
}
