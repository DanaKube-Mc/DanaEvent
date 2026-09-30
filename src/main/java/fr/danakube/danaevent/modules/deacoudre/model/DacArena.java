package fr.danakube.danaevent.modules.deacoudre.model;

import fr.danakube.danaevent.core.selection.CuboidRegion;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a configured Dé à Coudre arena.
 */
public class DacArena {

    private final String id;
    private String displayName;
    private CuboidRegion poolRegion;
    private Location divingLocation;
    private Location lobbyLocation;
    private int initialLives;
    private int jumpTimeSeconds;
    private JumpMode jumpMode;
    private DacGameFormat format;
    private DacTeamLifeMode teamLifeMode;
    private boolean enabled;

    public DacArena(@NotNull String id, @NotNull String displayName) {
        this(id, displayName, DacGameFormat.SOLO, JumpMode.TURN_BY_TURN);
    }

    public DacArena(
        @NotNull String id,
        @NotNull String displayName,
        @NotNull DacGameFormat format,
        @NotNull JumpMode jumpMode
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null").trim().toLowerCase();
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
        this.format = Objects.requireNonNull(format, "format cannot be null");
        this.jumpMode = Objects.requireNonNull(jumpMode, "jumpMode cannot be null");
        this.teamLifeMode = DacTeamLifeMode.LAST_STANDING;
        this.initialLives = 3;
        this.jumpTimeSeconds = 15;
        this.enabled = true;
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(@NotNull String displayName) {
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
    }

    public @Nullable CuboidRegion getPoolRegion() {
        return poolRegion;
    }

    public void setPoolRegion(@Nullable CuboidRegion poolRegion) {
        this.poolRegion = poolRegion;
    }

    public @Nullable Location getDivingLocation() {
        return divingLocation;
    }

    public void setDivingLocation(@Nullable Location divingLocation) {
        this.divingLocation = divingLocation != null ? divingLocation.clone() : null;
    }

    public @Nullable Location getLobbyLocation() {
        return lobbyLocation;
    }

    public void setLobbyLocation(@Nullable Location lobbyLocation) {
        this.lobbyLocation = lobbyLocation != null ? lobbyLocation.clone() : null;
    }

    public int getInitialLives() {
        return initialLives;
    }

    public void setInitialLives(int initialLives) {
        if (initialLives <= 0) {
            throw new IllegalArgumentException("initialLives must be positive");
        }
        this.initialLives = initialLives;
    }

    public int getJumpTimeSeconds() {
        return jumpTimeSeconds;
    }

    public void setJumpTimeSeconds(int jumpTimeSeconds) {
        if (jumpTimeSeconds <= 0) {
            throw new IllegalArgumentException("jumpTimeSeconds must be positive");
        }
        this.jumpTimeSeconds = jumpTimeSeconds;
    }

    public @NotNull JumpMode getJumpMode() {
        return jumpMode;
    }

    public void setJumpMode(@NotNull JumpMode jumpMode) {
        this.jumpMode = Objects.requireNonNull(jumpMode, "jumpMode cannot be null");
    }

    public @NotNull DacGameFormat getFormat() {
        return format;
    }

    public void setFormat(@NotNull DacGameFormat format) {
        this.format = Objects.requireNonNull(format, "format cannot be null");
    }

    public @NotNull DacTeamLifeMode getTeamLifeMode() {
        return teamLifeMode;
    }

    public void setTeamLifeMode(@NotNull DacTeamLifeMode teamLifeMode) {
        this.teamLifeMode = Objects.requireNonNull(teamLifeMode, "teamLifeMode cannot be null");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Checks if the arena has all required properties set to run matches.
     */
    public boolean isReady() {
        return poolRegion != null
            && divingLocation != null
            && lobbyLocation != null
            && initialLives > 0
            && jumpTimeSeconds > 0;
    }
}
