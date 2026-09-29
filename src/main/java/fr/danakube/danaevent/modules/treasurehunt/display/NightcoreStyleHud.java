package fr.danakube.danaevent.modules.treasurehunt.display;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * Renders Nightcore/ExcellentJobs style visual indicators (ActionBar with dynamic progress gauge,
 * shining completion Titles and Subtitles).
 */
public final class NightcoreStyleHud {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private NightcoreStyleHud() {
    }

    /**
     * Builds a text progress bar with filled green blocks and empty gray blocks.
     *
     * @param currentStep current 1-based step
     * @param totalSteps total step count
     * @param barLength number of blocks in the gauge
     * @return MiniMessage formatted bar string
     */
    public static String buildProgressBar(int currentStep, int totalSteps, int barLength) {
        if (totalSteps <= 0) {
            return "<gray>" + "■".repeat(Math.max(1, barLength)) + "</gray>";
        }
        int clampedStep = Math.max(0, Math.min(currentStep, totalSteps));
        int filled = (int) Math.round(((double) clampedStep / totalSteps) * barLength);
        filled = Math.max(0, Math.min(filled, barLength));
        int empty = barLength - filled;

        return "<green>" + "■".repeat(filled) + "</green><gray>" + "■".repeat(empty) + "</gray>";
    }

    /**
     * Builds the Nightcore styled ActionBar component.
     * Format: ⟨ CHASSE ⟩ Étape 3/5 [■■■■■] Indice : Près de l'ancien moulin...
     *
     * @param currentStep 1-based current step number
     * @param totalSteps total number of steps
     * @param clue current step clue
     * @return Adventure Component for ActionBar
     */
    public static Component buildActionBar(int currentStep, int totalSteps, @Nullable String clue) {
        String bar = buildProgressBar(currentStep, totalSteps, 5);
        String safeClue = clue != null && !clue.isBlank() ? clue : "Aucun indice disponible";

        String template = "⟨ <gold><bold>CHASSE</bold></gold> ⟩ " +
            "<yellow>Étape " + currentStep + "/" + totalSteps + "</yellow> " +
            "<dark_gray>[</dark_gray>" + bar + "<dark_gray>]</dark_gray> " +
            "<white>Indice : <italic>" + safeClue + "</italic></white>";

        return MINI_MESSAGE.deserialize(template);
    }

    /**
     * Sends the glowing objective completion title and subtitle to the player.
     *
     * @param player target player
     */
    public static void sendObjectiveCompletedTitle(@NotNull Player player) {
        Component mainTitle = MINI_MESSAGE.deserialize("<green><bold>OBJECTIF ACCOMPLI !</bold></green>");
        Component subTitle = MINI_MESSAGE.deserialize("<white>Nouvel indice débloqué (Ouvrez votre journal)</white>");

        Title title = Title.title(
            mainTitle,
            subTitle,
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2000), Duration.ofMillis(500))
        );
        player.showTitle(title);
    }

    /**
     * Sends the glorious hunt completion title and subtitle with the final time to the player.
     *
     * @param player target player
     * @param formattedTime formatted completion time (e.g. 05:23.142)
     */
    public static void sendHuntCompletedTitle(@NotNull Player player, @NotNull String formattedTime) {
        Component mainTitle = MINI_MESSAGE.deserialize("<gold><bold>CHASSE TERMINÉE !</bold></gold>");
        Component subTitle = MINI_MESSAGE.deserialize("<yellow>Temps total : <white>" + formattedTime + "</white></yellow>");

        Title title = Title.title(
            mainTitle,
            subTitle,
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(750))
        );
        player.showTitle(title);
    }
}
