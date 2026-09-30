package fr.danakube.danaevent.modules.chromaticsheep.model;

import org.bukkit.DyeColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents an active player session in a ChromaticSheep game.
 */
public class PlayerSheepSession {

    public static final long DEFAULT_BOMB_COOLDOWN_MILLIS = 15_000L;

    private final UUID playerUuid;
    private final UUID teamUuid;
    private final DyeColor color;
    private final String arenaId;

    private long lastBombThrownMillis;
    private int scorePoints;

    public PlayerSheepSession(
        @NotNull UUID playerUuid,
        @Nullable UUID teamUuid,
        @NotNull DyeColor color,
        @NotNull String arenaId
    ) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        this.teamUuid = teamUuid;
        this.color = Objects.requireNonNull(color, "color cannot be null");
        this.arenaId = Objects.requireNonNull(arenaId, "arenaId cannot be null").trim().toLowerCase();
        this.lastBombThrownMillis = 0L;
        this.scorePoints = 0;
    }

    public @NotNull UUID getPlayerUuid() {
        return playerUuid;
    }

    public @Nullable UUID getTeamUuid() {
        return teamUuid;
    }

    public boolean isTeam() {
        return teamUuid != null;
    }

    /**
     * Returns the UUID representing the score holder (team UUID if team mode, player UUID if solo).
     */
    public @NotNull UUID getEffectiveHolderUuid() {
        return teamUuid != null ? teamUuid : playerUuid;
    }

    public @NotNull DyeColor getColor() {
        return color;
    }

    public @NotNull String getArenaId() {
        return arenaId;
    }

    public boolean canThrowBomb(long cooldownMillis) {
        return System.currentTimeMillis() - lastBombThrownMillis >= cooldownMillis;
    }

    public boolean canThrowBomb() {
        return canThrowBomb(DEFAULT_BOMB_COOLDOWN_MILLIS);
    }

    public double getRemainingBombCooldownSeconds(long cooldownMillis) {
        long elapsed = System.currentTimeMillis() - lastBombThrownMillis;
        if (elapsed >= cooldownMillis) {
            return 0.0;
        }
        return Math.round((cooldownMillis - elapsed) / 100.0) / 10.0;
    }

    public double getRemainingBombCooldownSeconds() {
        return getRemainingBombCooldownSeconds(DEFAULT_BOMB_COOLDOWN_MILLIS);
    }

    public void recordBombThrow() {
        this.lastBombThrownMillis = System.currentTimeMillis();
    }

    public int getScorePoints() {
        return scorePoints;
    }

    public void addScore(int points) {
        this.scorePoints += points;
    }

    public void setScorePoints(int scorePoints) {
        this.scorePoints = scorePoints;
    }
}
