package fr.danakube.danaevent.modules.chromaticsheep.model;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.DyeColor;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the live state, sessions, boss bar, and tasks for an active ChromaticSheep game.
 */
public class SheepGame {

    private final SheepArena arena;
    private GameState state = GameState.WAITING;
    private final Map<UUID, PlayerSheepSession> sessions = new ConcurrentHashMap<>();
    private final Set<DyeColor> usedColors = new HashSet<>();

    private int remainingSeconds;
    private BossBar bossBar;

    private BukkitTask timerTask;
    private BukkitTask dominationTask;
    private BukkitTask hudTask;

    public SheepGame(@NotNull SheepArena arena) {
        this.arena = Objects.requireNonNull(arena, "arena cannot be null");
        this.remainingSeconds = arena.getDurationSeconds();
    }

    public @NotNull SheepArena getArena() {
        return arena;
    }

    public @NotNull GameState getState() {
        return state;
    }

    public void setState(@NotNull GameState state) {
        this.state = Objects.requireNonNull(state, "state cannot be null");
    }

    public @NotNull Map<UUID, PlayerSheepSession> getSessions() {
        return Collections.unmodifiableMap(sessions);
    }

    public void addSession(@NotNull PlayerSheepSession session) {
        Objects.requireNonNull(session, "session cannot be null");
        sessions.put(session.getPlayerUuid(), session);
        usedColors.add(session.getColor());
    }

    public @Nullable PlayerSheepSession removeSession(@NotNull UUID playerUuid) {
        PlayerSheepSession removed = sessions.remove(playerUuid);
        if (removed != null) {
            // Check if any other session uses this color
            boolean colorStillUsed = sessions.values().stream().anyMatch(s -> s.getColor() == removed.getColor());
            if (!colorStillUsed) {
                usedColors.remove(removed.getColor());
            }
        }
        return removed;
    }

    public boolean isColorUsed(@NotNull DyeColor color) {
        return usedColors.contains(color);
    }

    public @NotNull Set<DyeColor> getUsedColors() {
        return Collections.unmodifiableSet(usedColors);
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public int decrementSeconds() {
        if (remainingSeconds > 0) {
            remainingSeconds--;
        }
        return remainingSeconds;
    }

    public @Nullable BossBar getBossBar() {
        return bossBar;
    }

    public void setBossBar(@Nullable BossBar bossBar) {
        this.bossBar = bossBar;
    }

    public void setTimerTask(@Nullable BukkitTask timerTask) {
        this.timerTask = timerTask;
    }

    public void setDominationTask(@Nullable BukkitTask dominationTask) {
        this.dominationTask = dominationTask;
    }

    public void setHudTask(@Nullable BukkitTask hudTask) {
        this.hudTask = hudTask;
    }

    public void cancelAllTasks() {
        if (timerTask != null && !timerTask.isCancelled()) {
            timerTask.cancel();
        }
        if (dominationTask != null && !dominationTask.isCancelled()) {
            dominationTask.cancel();
        }
        if (hudTask != null && !hudTask.isCancelled()) {
            hudTask.cancel();
        }
    }
}
