package fr.danakube.danaevent.modules.chromaticsheep.model;

import fr.danakube.danaevent.core.selection.CuboidRegion;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a configured ChromaticSheep arena.
 */
public class SheepArena {

    private final String id;
    private String displayName;
    private CuboidRegion bounds;
    private final List<Location> playerSpawns = new ArrayList<>();
    private int sheepCount;
    private int durationSeconds;
    private GameFormat format;
    private ScoringMode scoringMode;
    private boolean enabled;

    public SheepArena(@NotNull String id, @NotNull String displayName) {
        this(id, displayName, GameFormat.SOLO, ScoringMode.FINAL_COUNT);
    }

    public SheepArena(
        @NotNull String id,
        @NotNull String displayName,
        @NotNull GameFormat format,
        @NotNull ScoringMode scoringMode
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null").trim().toLowerCase();
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
        this.format = Objects.requireNonNull(format, "format cannot be null");
        this.scoringMode = Objects.requireNonNull(scoringMode, "scoringMode cannot be null");
        this.sheepCount = 40;
        this.durationSeconds = 120;
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

    public @Nullable CuboidRegion getBounds() {
        return bounds;
    }

    public void setBounds(@Nullable CuboidRegion bounds) {
        this.bounds = bounds;
    }

    public @NotNull List<Location> getPlayerSpawns() {
        return Collections.unmodifiableList(playerSpawns);
    }

    public void addPlayerSpawn(@NotNull Location location) {
        Objects.requireNonNull(location, "location cannot be null");
        this.playerSpawns.add(location.clone());
    }

    public boolean removePlayerSpawn(int index) {
        if (index >= 0 && index < playerSpawns.size()) {
            playerSpawns.remove(index);
            return true;
        }
        return false;
    }

    public void clearPlayerSpawns() {
        this.playerSpawns.clear();
    }

    public void setPlayerSpawns(@NotNull List<Location> spawns) {
        Objects.requireNonNull(spawns, "spawns cannot be null");
        this.playerSpawns.clear();
        for (Location loc : spawns) {
            if (loc != null) {
                this.playerSpawns.add(loc.clone());
            }
        }
    }

    public int getSheepCount() {
        return sheepCount;
    }

    public void setSheepCount(int sheepCount) {
        if (sheepCount <= 0) {
            throw new IllegalArgumentException("sheepCount must be positive");
        }
        this.sheepCount = sheepCount;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
        this.durationSeconds = durationSeconds;
    }

    public @NotNull GameFormat getFormat() {
        return format;
    }

    public void setFormat(@NotNull GameFormat format) {
        this.format = Objects.requireNonNull(format, "format cannot be null");
    }

    public @NotNull ScoringMode getScoringMode() {
        return scoringMode;
    }

    public void setScoringMode(@NotNull ScoringMode scoringMode) {
        this.scoringMode = Objects.requireNonNull(scoringMode, "scoringMode cannot be null");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Checks if the arena is fully configured and ready to host games.
     */
    public boolean isReady() {
        return bounds != null && !playerSpawns.isEmpty() && sheepCount > 0 && durationSeconds > 0;
    }
}
