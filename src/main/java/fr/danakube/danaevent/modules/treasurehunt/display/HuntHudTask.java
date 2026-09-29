package fr.danakube.danaevent.modules.treasurehunt.display;

import fr.danakube.danaevent.modules.treasurehunt.config.HuntConfig;
import fr.danakube.danaevent.modules.treasurehunt.manager.HuntProgressManager;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntStep;
import fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Periodically updates the Nightcore-style ActionBar HUD for all active hunt participants.
 */
public class HuntHudTask extends BukkitRunnable {

    private final HuntProgressManager progressManager;
    private final HuntConfig huntConfig;

    public HuntHudTask(@NotNull HuntProgressManager progressManager, @NotNull HuntConfig huntConfig) {
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
            int currentStepIndex = progress.getCurrentStepIndex();
            int currentStepNum = progress.getActiveStepNumber();
            int totalSteps = progress.getStepOrder().size();

            Optional<HuntStep> stepOpt = hunt.getStep(currentStepNum);
            String clue = stepOpt.map(HuntStep::getClue).orElse("Cherchez le trésor...");

            Component actionBar = NightcoreStyleHud.buildActionBar(currentStepIndex + 1, totalSteps, clue);
            player.sendActionBar(actionBar);
        }
    }
}
