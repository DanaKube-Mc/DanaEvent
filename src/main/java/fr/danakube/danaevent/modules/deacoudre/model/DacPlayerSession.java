package fr.danakube.danaevent.modules.deacoudre.model;

import org.bukkit.DyeColor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Tracks an active player's state during a Dé à Coudre match.
 */
public class DacPlayerSession {

    private final UUID playerUuid;
    private final String arenaId;
    private final DyeColor color;
    private final UUID effectiveHolderUuid;
    private final boolean isTeam;

    private int lives;
    private int successfulJumps;
    private int perfectDacs;
    private boolean spectator;
    private boolean jumping;

    public DacPlayerSession(
        @NotNull UUID playerUuid,
        @NotNull String arenaId,
        @NotNull DyeColor color,
        @NotNull UUID effectiveHolderUuid,
        boolean isTeam,
        int initialLives
    ) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        this.arenaId = Objects.requireNonNull(arenaId, "arenaId cannot be null").trim().toLowerCase();
        this.color = Objects.requireNonNull(color, "color cannot be null");
        this.effectiveHolderUuid = Objects.requireNonNull(effectiveHolderUuid, "effectiveHolderUuid cannot be null");
        this.isTeam = isTeam;
        this.lives = Math.max(1, initialLives);
        this.successfulJumps = 0;
        this.perfectDacs = 0;
        this.spectator = false;
    }

    public @NotNull UUID getPlayerUuid() {
        return playerUuid;
    }

    public @NotNull String getArenaId() {
        return arenaId;
    }

    public @NotNull DyeColor getColor() {
        return color;
    }

    public @NotNull UUID getEffectiveHolderUuid() {
        return effectiveHolderUuid;
    }

    public boolean isTeam() {
        return isTeam;
    }

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {
        this.lives = lives;
    }

    public boolean decrementLife() {
        if (lives > 0) {
            lives--;
            if (lives <= 0) {
                spectator = true;
            }
            return true;
        }
        return false;
    }

    public boolean incrementLife(int maxLives) {
        if (lives < maxLives) {
            lives++;
            return true;
        }
        return false;
    }

    public int getSuccessfulJumps() {
        return successfulJumps;
    }

    public void incrementSuccessfulJumps() {
        this.successfulJumps++;
    }

    public int getPerfectDacs() {
        return perfectDacs;
    }

    public void incrementPerfectDacs() {
        this.perfectDacs++;
    }

    public boolean isSpectator() {
        return spectator;
    }

    public void setSpectator(boolean spectator) {
        this.spectator = spectator;
    }

    public boolean isJumping() {
        return jumping;
    }

    public void setJumping(boolean jumping) {
        this.jumping = jumping;
    }

    public boolean isAlive() {
        return lives > 0 && !spectator;
    }
}
