package fr.danakube.danaevent.modules.chromaticsheep.task;

import fr.danakube.danaevent.modules.chromaticsheep.manager.SheepGameManager;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepGame;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Decrements the match countdown, updates the BossBar color and progress, and triggers match end.
 */
public class GameTimerTask implements Runnable {

    private final SheepGameManager gameManager;
    private final String arenaId;

    public GameTimerTask(@NotNull SheepGameManager gameManager, @NotNull String arenaId) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager cannot be null");
        this.arenaId = Objects.requireNonNull(arenaId, "arenaId cannot be null");
    }

    @Override
    public void run() {
        SheepGame game = gameManager.getGame(arenaId);
        if (game == null) {
            return;
        }

        int remaining = game.decrementSeconds();
        int total = game.getArena().getDurationSeconds();

        // Update BossBar
        BossBar bossBar = game.getBossBar();
        if (bossBar != null) {
            float progress = Math.max(0.0f, Math.min(1.0f, (float) remaining / (float) Math.max(1, total)));
            bossBar.progress(progress);

            int minutes = remaining / 60;
            int seconds = remaining % 60;
            String timeFormatted = String.format("%02d:%02d", minutes, seconds);

            bossBar.name(MiniMessage.miniMessage().deserialize(
                "<!italic><gradient:#ff5555:#ffff55><b>MoutonChromatique</b></gradient> <dark_gray>»</dark_gray> <white>Temps restant : <yellow>" + timeFormatted + "</yellow></white>"
            ));

            if (remaining > 30) {
                bossBar.color(BossBar.Color.GREEN);
            } else if (remaining > 10) {
                bossBar.color(BossBar.Color.YELLOW);
            } else {
                bossBar.color(BossBar.Color.RED);
            }
        }

        // Countdown audio & title alerts for final 5 seconds
        if (remaining <= 5 && remaining > 0) {
            for (UUID uuid : game.getSessions().keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                    player.showTitle(Title.title(
                        MiniMessage.miniMessage().deserialize("<red><b>" + remaining + "</b></red>"),
                        MiniMessage.miniMessage().deserialize("<gray>Dernières secondes !</gray>"),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(200))
                    ));
                }
            }
        }

        // Timer ended
        if (remaining <= 0) {
            gameManager.endGame(arenaId);
        }
    }
}
