package fr.danakube.danaevent.modules.deacoudre.task;

import fr.danakube.danaevent.modules.deacoudre.manager.DacGameManager;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacGame;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameState;
import fr.danakube.danaevent.modules.deacoudre.model.DacPlayerSession;
import fr.danakube.danaevent.modules.deacoudre.model.JumpMode;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Manages the countdown timer, BossBar gradient, and heartbeat audio for jump turns and waves.
 */
public class JumpCountdownTask extends BukkitRunnable {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final DacGame game;
    private final DacGameManager gameManager;
    private final @Nullable UUID currentJumperUuid;
    private final int totalSeconds;
    private int remainingSeconds;

    private BossBar bossBar;

    public JumpCountdownTask(
        @NotNull DacGame game,
        @NotNull DacGameManager gameManager,
        @Nullable UUID currentJumperUuid,
        int totalSeconds
    ) {
        this.game = Objects.requireNonNull(game, "game cannot be null");
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager cannot be null");
        this.currentJumperUuid = currentJumperUuid;
        this.totalSeconds = Math.max(1, totalSeconds);
        this.remainingSeconds = this.totalSeconds;

        initBossBar();
    }

    private void initBossBar() {
        this.bossBar = BossBar.bossBar(
            buildBossBarTitle(),
            1.0f,
            BossBar.Color.GREEN,
            BossBar.Overlay.PROGRESS
        );

        showBossBarToAll();
    }

    private void showBossBarToAll() {
        for (UUID uuid : game.getParticipants().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.showBossBar(bossBar);
            }
        }
    }

    public void hideBossBarFromAll() {
        if (bossBar == null) {
            return;
        }
        for (UUID uuid : game.getParticipants().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.hideBossBar(bossBar);
            }
        }
    }

    public @NotNull BossBar getBossBar() {
        return bossBar;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    @Override
    public void run() {
        if (game.getState() != DacGameState.IN_GAME) {
            cancelAndClean();
            return;
        }

        // In TURN_BY_TURN: if jumper is no longer jumping (e.g. jumped or disconnected), stop task
        if (game.getArena().getJumpMode() == JumpMode.TURN_BY_TURN && currentJumperUuid != null) {
            DacPlayerSession session = game.getSession(currentJumperUuid);
            if (session == null || !session.isJumping()) {
                cancelAndClean();
                return;
            }
        }

        // In WAVE mode: if all jumped, stop task
        if (game.getArena().getJumpMode() == JumpMode.SIMULTANEOUS_WAVE && game.isWaveComplete()) {
            cancelAndClean();
            return;
        }

        remainingSeconds--;

        float progress = Math.max(0.0f, Math.min(1.0f, (float) remainingSeconds / (float) totalSeconds));
        bossBar.progress(progress);

        // Update color gradient Green -> Yellow -> Red
        if (progress > 0.5f) {
            bossBar.color(BossBar.Color.GREEN);
        } else if (progress > 0.25f) {
            bossBar.color(BossBar.Color.YELLOW);
        } else {
            bossBar.color(BossBar.Color.RED);
        }

        bossBar.name(buildBossBarTitle());

        // Heartbeat sound in last 5 seconds
        if (remainingSeconds <= 5 && remainingSeconds > 0) {
            playHeartbeatSound();
        }

        if (remainingSeconds <= 0) {
            cancelAndClean();
            handleTimeout();
        }
    }

    private net.kyori.adventure.text.Component buildBossBarTitle() {
        if (game.getArena().getJumpMode() == JumpMode.SIMULTANEOUS_WAVE) {
            return MM.deserialize("<gradient:#ff416c:#ff4b2b><b>[Vague " + game.getWaveNumber() + "]</b></gradient> <gray>Saut dans :</gray> <yellow>" + remainingSeconds + "s</yellow>");
        }

        String jumperName = "Inconnu";
        if (currentJumperUuid != null) {
            Player p = Bukkit.getPlayer(currentJumperUuid);
            if (p != null) {
                jumperName = p.getName();
            }
        }

        return MM.deserialize("<gradient:#00c6ff:#0072ff><b>[Tour " + game.getCurrentRound() + " : " + jumperName + "]</b></gradient> <gray>Temps restant :</gray> <yellow>" + remainingSeconds + "s</yellow>");
    }

    private void playHeartbeatSound() {
        for (UUID uuid : game.getParticipants().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                try {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 1.2f);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void handleTimeout() {
        if (currentJumperUuid != null) {
            Player p = Bukkit.getPlayer(currentJumperUuid);
            if (p != null && p.isOnline()) {
                gameManager.handleTimeout(p);
            }
        } else if (game.getArena().getJumpMode() == JumpMode.SIMULTANEOUS_WAVE) {
            // For players who failed to jump in wave
            for (UUID uuid : game.getAlivePlayers()) {
                DacPlayerSession s = game.getSession(uuid);
                if (s != null && s.isJumping()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        gameManager.handleTimeout(p);
                    }
                }
            }
        }
    }

    public void cancelAndClean() {
        try {
            cancel();
        } catch (IllegalStateException ignored) {
        }
        hideBossBarFromAll();
    }
}
