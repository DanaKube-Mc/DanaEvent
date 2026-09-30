package fr.danakube.danaevent.modules.chromaticsheep.model;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Sheep;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles PDC metadata attached to game sheep in ChromaticSheep.
 */
public final class SheepData {

    public static final NamespacedKey KEY_ARENA = new NamespacedKey("danaevent", "mc_sheep_arena");
    public static final NamespacedKey KEY_TYPE = new NamespacedKey("danaevent", "mc_sheep_type");
    public static final NamespacedKey KEY_PROTECTED_UNTIL = new NamespacedKey("danaevent", "mc_protected_until");
    public static final NamespacedKey KEY_LAST_DYED_BY = new NamespacedKey("danaevent", "mc_last_dyed_by");
    public static final NamespacedKey KEY_LAST_DYED_TIME = new NamespacedKey("danaevent", "mc_last_dyed_time");

    private SheepData() {
    }

    /**
     * Tags a sheep as belonging to an active ChromaticSheep arena game.
     */
    public static void tagSheep(@NotNull Sheep sheep, @NotNull String arenaId, @NotNull SpecialSheepType type) {
        Objects.requireNonNull(sheep, "sheep cannot be null");
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        PersistentDataContainer pdc = sheep.getPersistentDataContainer();
        pdc.set(KEY_ARENA, PersistentDataType.STRING, arenaId.trim().toLowerCase());
        pdc.set(KEY_TYPE, PersistentDataType.STRING, type.name());
        pdc.set(KEY_PROTECTED_UNTIL, PersistentDataType.LONG, 0L);
        pdc.set(KEY_LAST_DYED_TIME, PersistentDataType.LONG, 0L);
    }

    /**
     * Checks if an entity is a registered game sheep.
     */
    public static boolean isGameSheep(@Nullable Sheep sheep) {
        if (sheep == null) {
            return false;
        }
        return sheep.getPersistentDataContainer().has(KEY_ARENA, PersistentDataType.STRING);
    }

    /**
     * Retrieves the arena ID associated with this sheep.
     */
    public static Optional<String> getArenaId(@Nullable Sheep sheep) {
        if (sheep == null) {
            return Optional.empty();
        }
        String arenaId = sheep.getPersistentDataContainer().get(KEY_ARENA, PersistentDataType.STRING);
        return Optional.ofNullable(arenaId);
    }

    /**
     * Retrieves the special type of the sheep (defaults to NORMAL).
     */
    public static @NotNull SpecialSheepType getSpecialType(@Nullable Sheep sheep) {
        if (sheep == null) {
            return SpecialSheepType.NORMAL;
        }
        String raw = sheep.getPersistentDataContainer().get(KEY_TYPE, PersistentDataType.STRING);
        return SpecialSheepType.fromString(raw);
    }

    /**
     * Updates the special type of the sheep.
     */
    public static void setSpecialType(@NotNull Sheep sheep, @NotNull SpecialSheepType type) {
        Objects.requireNonNull(sheep, "sheep cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        sheep.getPersistentDataContainer().set(KEY_TYPE, PersistentDataType.STRING, type.name());
    }

    /**
     * Checks whether the sheep is currently protected from being dyed.
     */
    public static boolean isProtected(@Nullable Sheep sheep) {
        if (sheep == null) {
            return false;
        }
        Long until = sheep.getPersistentDataContainer().get(KEY_PROTECTED_UNTIL, PersistentDataType.LONG);
        return until != null && System.currentTimeMillis() < until;
    }

    /**
     * Sets the protection timestamp until which the sheep cannot be dyed by rivals.
     */
    public static void setProtectedUntil(@NotNull Sheep sheep, long untilMillis) {
        Objects.requireNonNull(sheep, "sheep cannot be null");
        sheep.getPersistentDataContainer().set(KEY_PROTECTED_UNTIL, PersistentDataType.LONG, untilMillis);
    }

    /**
     * Retrieves the UUID of the holder (player or team) who last dyed this sheep.
     */
    public static Optional<UUID> getLastDyedBy(@Nullable Sheep sheep) {
        if (sheep == null) {
            return Optional.empty();
        }
        String raw = sheep.getPersistentDataContainer().get(KEY_LAST_DYED_BY, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Sets the holder UUID and timestamp when the sheep was dyed.
     */
    public static void setLastDyed(@NotNull Sheep sheep, @NotNull UUID holderUuid) {
        Objects.requireNonNull(sheep, "sheep cannot be null");
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");

        long now = System.currentTimeMillis();
        PersistentDataContainer pdc = sheep.getPersistentDataContainer();
        pdc.set(KEY_LAST_DYED_BY, PersistentDataType.STRING, holderUuid.toString());
        pdc.set(KEY_LAST_DYED_TIME, PersistentDataType.LONG, now);
    }

    /**
     * Gets the timestamp when the sheep was last dyed.
     */
    public static long getLastDyedTime(@Nullable Sheep sheep) {
        if (sheep == null) {
            return 0L;
        }
        Long time = sheep.getPersistentDataContainer().get(KEY_LAST_DYED_TIME, PersistentDataType.LONG);
        return time != null ? time : 0L;
    }
}
