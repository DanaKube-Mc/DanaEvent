package fr.danakube.danaevent.modules.chromaticsheep.manager;

import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepArena;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepData;
import fr.danakube.danaevent.modules.chromaticsheep.model.SpecialSheepType;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Sheep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the sheep herd life cycle, spawning, special variants, and entity cleanup.
 */
public class HerdManager {

    private final Map<String, Set<UUID>> arenaSheep = new ConcurrentHashMap<>();
    private final Random random = new Random();

    /**
     * Spawns a full herd of sheep inside the arena bounds.
     */
    public List<Sheep> spawnHerd(@NotNull SheepArena arena, @NotNull World world) {
        Objects.requireNonNull(arena, "arena cannot be null");
        Objects.requireNonNull(world, "world cannot be null");

        CuboidRegion bounds = arena.getBounds();
        if (bounds == null) {
            throw new IllegalStateException("Arena bounds must be defined before spawning herd");
        }

        String arenaId = arena.getId();
        cleanUpArena(arenaId, world);

        Set<UUID> sheepSet = arenaSheep.computeIfAbsent(arenaId, k -> ConcurrentHashMap.newKeySet());
        List<Sheep> spawned = new ArrayList<>();

        int count = arena.getSheepCount();
        for (int i = 0; i < count; i++) {
            Location loc = getRandomLocation(bounds, world);

            Sheep sheep = (Sheep) world.spawnEntity(loc, EntityType.SHEEP);
            sheep.setAdult();
            sheep.setAgeLock(true);
            sheep.setBreed(false);
            try {
                sheep.setRemoveWhenFarAway(false);
            } catch (Throwable ignored) {
                // MockBukkit does not implement setRemoveWhenFarAway
            }

            // Determine if special variant (approx 5% golden, 5% rainbow, 5% trickster)
            SpecialSheepType type = pickRandomType();
            configureSheepType(sheep, type, arenaId);

            sheepSet.add(sheep.getUniqueId());
            spawned.add(sheep);
        }

        return spawned;
    }

    /**
     * Configures visuals and PDC tags for a sheep based on its special type.
     */
    public void configureSheepType(@NotNull Sheep sheep, @NotNull SpecialSheepType type, @NotNull String arenaId) {
        SheepData.tagSheep(sheep, arenaId, type);

        switch (type) {
            case GOLDEN -> {
                sheep.setColor(DyeColor.YELLOW);
                sheep.setGlowing(true);
                sheep.customName(null);
            }
            case RAINBOW -> {
                sheep.setColor(DyeColor.WHITE);
                sheep.setGlowing(false);
                sheep.setCustomName("jeb_");
                sheep.setCustomNameVisible(false);
            }
            case TRICKSTER -> {
                sheep.setColor(DyeColor.BLACK);
                sheep.setGlowing(false);
                sheep.customName(null);
            }
            case NORMAL -> {
                sheep.setColor(DyeColor.WHITE);
                sheep.setGlowing(false);
                sheep.customName(null);
            }
        }
    }

    /**
     * Picks a random sheep type with weighted probabilities.
     */
    private SpecialSheepType pickRandomType() {
        int roll = random.nextInt(100);
        if (roll < 5) {
            return SpecialSheepType.GOLDEN;
        } else if (roll < 10) {
            return SpecialSheepType.RAINBOW;
        } else if (roll < 15) {
            return SpecialSheepType.TRICKSTER;
        }
        return SpecialSheepType.NORMAL;
    }

    /**
     * Finds a random coordinate within the cuboid region.
     */
    private Location getRandomLocation(CuboidRegion bounds, World world) {
        int minX = bounds.getMinX();
        int maxX = bounds.getMaxX();
        int minY = bounds.getMinY();
        int maxY = bounds.getMaxY();
        int minZ = bounds.getMinZ();
        int maxZ = bounds.getMaxZ();

        double x = minX + (maxX > minX ? random.nextDouble() * (maxX - minX) : 0.5);
        double y = Math.min(minY + 1.0, maxY);
        double z = minZ + (maxZ > minZ ? random.nextDouble() * (maxZ - minZ) : 0.5);

        return new Location(world, x, y, z);
    }

    /**
     * Retrieves all living sheep in an arena within a given radius of a location.
     */
    public List<Sheep> getNearbyGameSheep(@NotNull Location center, double radius, @NotNull String arenaId) {
        Objects.requireNonNull(center, "center cannot be null");
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        World world = center.getWorld();
        if (world == null) {
            return List.of();
        }

        Set<UUID> tracked = arenaSheep.get(arenaId.trim().toLowerCase());
        if (tracked == null || tracked.isEmpty()) {
            return List.of();
        }

        double radiusSquared = radius * radius;
        List<Sheep> result = new ArrayList<>();

        for (UUID uuid : tracked) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof Sheep sheep && sheep.isValid()) {
                if (sheep.getWorld().equals(world) && sheep.getLocation().distanceSquared(center) <= radiusSquared) {
                    result.add(sheep);
                }
            }
        }
        return result;
    }

    /**
     * Counts how many sheep are colored with each DyeColor in the given arena.
     */
    public Map<DyeColor, Integer> countColors(@NotNull String arenaId, @NotNull World world) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        Objects.requireNonNull(world, "world cannot be null");

        Map<DyeColor, Integer> counts = new EnumMap<>(DyeColor.class);
        for (DyeColor color : DyeColor.values()) {
            counts.put(color, 0);
        }

        Set<UUID> tracked = arenaSheep.get(arenaId.trim().toLowerCase());
        if (tracked == null || tracked.isEmpty()) {
            return counts;
        }

        for (UUID uuid : tracked) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof Sheep sheep && sheep.isValid() && sheep.getColor() != null) {
                counts.put(sheep.getColor(), counts.get(sheep.getColor()) + 1);
            }
        }
        return counts;
    }

    /**
     * Gets all sheep UUIDs tracked for an arena.
     */
    public Set<UUID> getSheep(@Nullable String arenaId) {
        if (arenaId == null) {
            return Collections.emptySet();
        }
        Set<UUID> set = arenaSheep.get(arenaId.trim().toLowerCase());
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }

    /**
     * Explicitly tracks a sheep UUID for an arena.
     */
    public void trackSheep(@NotNull String arenaId, @NotNull UUID sheepUuid) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        Objects.requireNonNull(sheepUuid, "sheepUuid cannot be null");
        arenaSheep.computeIfAbsent(arenaId.trim().toLowerCase(), k -> ConcurrentHashMap.newKeySet()).add(sheepUuid);
    }

    /**
     * Checks if a sheep UUID is tracked as a game sheep.
     */
    public boolean isTrackedSheep(@Nullable UUID sheepUuid) {
        if (sheepUuid == null) {
            return false;
        }
        for (Set<UUID> set : arenaSheep.values()) {
            if (set.contains(sheepUuid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes and despanws all sheep for a specific arena.
     */
    public void cleanUpArena(@NotNull String arenaId, @Nullable World world) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        Set<UUID> tracked = arenaSheep.remove(arenaId.trim().toLowerCase());
        if (tracked == null || tracked.isEmpty()) {
            return;
        }

        for (UUID uuid : tracked) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
    }

    /**
     * Absolute cleanup: cleans all sheep across all arenas.
     */
    public void cleanUpAll() {
        for (Set<UUID> tracked : arenaSheep.values()) {
            for (UUID uuid : tracked) {
                Entity entity = Bukkit.getEntity(uuid);
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
            }
        }
        arenaSheep.clear();
    }
}
