package fr.danakube.danaevent.modules.treasurehunt.listener;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.modules.treasurehunt.config.HuntConfig;
import fr.danakube.danaevent.modules.treasurehunt.manager.HuntProgressManager;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntStep;
import fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress;
import fr.danakube.danaevent.modules.treasurehunt.model.StepTriggerType;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for chat messages to validate CHAT_ANSWER objectives.
 * Intercepts matching secret answers and cancels the event to avoid leaking answers to nearby players.
 */
public class HuntChatAnswerListener implements Listener {

    private final DanaEventPlugin plugin;
    private final HuntProgressManager progressManager;
    private final HuntConfig huntConfig;
    private final Map<UUID, Long> processedAnswers = new ConcurrentHashMap<>();

    public HuntChatAnswerListener(
        @NotNull DanaEventPlugin plugin,
        @NotNull HuntProgressManager progressManager,
        @NotNull HuntConfig huntConfig
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.progressManager = Objects.requireNonNull(progressManager, "progressManager cannot be null");
        this.huntConfig = Objects.requireNonNull(huntConfig, "huntConfig cannot be null");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (handleChat(event.getPlayer(), message)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onLegacyAsyncPlayerChat(AsyncPlayerChatEvent event) {
        if (handleChat(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
        }
    }

    private boolean handleChat(@NotNull Player player, @NotNull String message) {
        Optional<PlayerHuntProgress> progressOpt = progressManager.getProgressForPlayer(player);
        if (progressOpt.isEmpty()) {
            return false;
        }

        PlayerHuntProgress progress = progressOpt.get();
        Optional<Hunt> huntOpt = huntConfig.getHunt(progress.getHuntId());
        if (huntOpt.isEmpty() || !huntOpt.get().isEnabled()) {
            return false;
        }

        Hunt hunt = huntOpt.get();
        int activeStepNum = progress.getActiveStepNumber();
        Optional<HuntStep> activeStepOpt = hunt.getStep(activeStepNum);

        // Check if message matches the active step answer
        if (activeStepOpt.isPresent() && activeStepOpt.get().matchesChatAnswer(message)) {
            long now = System.currentTimeMillis();
            Long last = processedAnswers.get(player.getUniqueId());
            if (last != null && now - last < 1000L) {
                return true; // Already processed, consume duplicate event
            }
            processedAnswers.put(player.getUniqueId(), now);

            runSync(() -> progressManager.validateStepForPlayer(player, activeStepNum));
            return true;
        }

        // Check if message matches any other step's chat answer in this hunt
        boolean matchesOtherStep = hunt.getSteps().stream()
            .filter(step -> step.getTriggerType() == StepTriggerType.CHAT_ANSWER)
            .anyMatch(step -> step.matchesChatAnswer(message));

        if (matchesOtherStep) {
            long now = System.currentTimeMillis();
            Long last = processedAnswers.get(player.getUniqueId());
            if (last != null && now - last < 1000L) {
                return true;
            }
            processedAnswers.put(player.getUniqueId(), now);

            runSync(() -> plugin.getMessageManager().sendMessage(player, "hunt-wrong-step"));
            return true;
        }

        return false;
    }

    private void runSync(@NotNull Runnable action) {
        if (plugin != null && plugin.isEnabled() && !Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, action);
        } else {
            action.run();
        }
    }

    public void cleanUp() {
        processedAnswers.clear();
    }
}
