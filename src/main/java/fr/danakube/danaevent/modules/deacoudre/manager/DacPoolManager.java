package fr.danakube.danaevent.modules.deacoudre.manager;

import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacPoolBlock;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Dé à Coudre pool block states, water detection, wool conversions,
 * Perfect DAC calculations, and clean instant rollbacks.
 */
public class DacPoolManager {

    private final Map<String, List<DacPoolBlock>> poolSnapshots = new ConcurrentHashMap<>();

    /**
     * Captures a snapshot of the arena pool before match start.
     */
    public void captureSnapshot(@NotNull DacArena arena, @NotNull World world) {
        Objects.requireNonNull(arena, "arena cannot be null");
        Objects.requireNonNull(world, "world cannot be null");

        CuboidRegion region = arena.getPoolRegion();
        if (region == null) {
            return;
        }

        List<DacPoolBlock> blocks = new ArrayList<>();
        int minX = region.getMinX();
        int maxX = region.getMaxX();
        int minY = region.getMinY();
        int maxY = region.getMaxY();
        int minZ = region.getMinZ();
        int maxZ = region.getMaxZ();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    blocks.add(new DacPoolBlock(x, y, z, block.getBlockData().clone()));
                }
            }
        }

        poolSnapshots.put(arena.getId().trim().toLowerCase(), Collections.unmodifiableList(blocks));
    }

    /**
     * Checks if a location is strictly within the arena pool region.
     */
    public boolean isInsidePool(@Nullable Location loc, @NotNull DacArena arena) {
        if (loc == null || arena.getPoolRegion() == null) {
            return false;
        }
        return arena.getPoolRegion().contains(loc);
    }

    /**
     * Checks whether a block is stationary or flowing water.
     */
    public boolean isWater(@Nullable Block block) {
        return block != null && block.getType() == Material.WATER;
    }

    /**
     * Checks whether a block is any wool material.
     */
    public boolean isWool(@Nullable Block block) {
        if (block == null) {
            return false;
        }
        return block.getType().name().endsWith("_WOOL");
    }

    /**
     * Evaluates whether landing on this water block constitutes a Perfect DAC (1x1 surrounded by 4 wool blocks).
     */
    public boolean isPerfectDac(@NotNull Block block) {
        Objects.requireNonNull(block, "block cannot be null");
        if (!isWater(block)) {
            return false;
        }

        World world = block.getWorld();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        Block north = world.getBlockAt(x, y, z - 1);
        Block south = world.getBlockAt(x, y, z + 1);
        Block east = world.getBlockAt(x + 1, y, z);
        Block west = world.getBlockAt(x - 1, y, z);

        return isWool(north) && isWool(south) && isWool(east) && isWool(west);
    }

    /**
     * Converts the given block to wool of the specified dye color.
     */
    public void convertToWool(@NotNull Block block, @NotNull DyeColor color) {
        Objects.requireNonNull(block, "block cannot be null");
        Objects.requireNonNull(color, "color cannot be null");

        Material woolMaterial = getWoolMaterial(color);
        block.setType(woolMaterial, false);
    }

    /**
     * Resolves the Wool Material corresponding to a DyeColor.
     */
    public static Material getWoolMaterial(@NotNull DyeColor color) {
        try {
            return Material.valueOf(color.name() + "_WOOL");
        } catch (IllegalArgumentException e) {
            return Material.WHITE_WOOL;
        }
    }

    /**
     * Counts remaining water blocks within the pool region.
     */
    public int getRemainingWaterCount(@NotNull DacArena arena, @NotNull World world) {
        Objects.requireNonNull(arena, "arena cannot be null");
        Objects.requireNonNull(world, "world cannot be null");

        CuboidRegion region = arena.getPoolRegion();
        if (region == null) {
            return 0;
        }

        int count = 0;
        int minX = region.getMinX();
        int maxX = region.getMaxX();
        int minY = region.getMinY();
        int maxY = region.getMaxY();
        int minZ = region.getMinZ();
        int maxZ = region.getMaxZ();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.WATER) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Instantly rolls back the arena pool to its pre-match state, restoring stationary water.
     */
    public void rollbackPool(@NotNull DacArena arena, @NotNull World world) {
        Objects.requireNonNull(arena, "arena cannot be null");
        Objects.requireNonNull(world, "world cannot be null");

        String arenaId = arena.getId().trim().toLowerCase();
        List<DacPoolBlock> snapshot = poolSnapshots.remove(arenaId);

        if (snapshot != null) {
            for (DacPoolBlock pb : snapshot) {
                Block block = world.getBlockAt(pb.x(), pb.y(), pb.z());
                try {
                    block.setBlockData(pb.originalData(), false);
                } catch (RuntimeException e) {
                    try {
                        block.setBlockData(pb.originalData());
                    } catch (RuntimeException ignored) {
                        block.setType(pb.originalData().getMaterial());
                    }
                }
            }
        } else if (arena.getPoolRegion() != null) {
            // Fallback: fill pool region with pure stationary water
            CuboidRegion region = arena.getPoolRegion();
            BlockData pureWater = Material.WATER.createBlockData();
            if (pureWater instanceof Levelled levelled) {
                levelled.setLevel(0);
            }

            int minX = region.getMinX();
            int maxX = region.getMaxX();
            int minY = region.getMinY();
            int maxY = region.getMaxY();
            int minZ = region.getMinZ();
            int maxZ = region.getMaxZ();

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Block block = world.getBlockAt(x, y, z);
                        try {
                            block.setBlockData(pureWater, false);
                        } catch (RuntimeException e) {
                            try {
                                block.setBlockData(pureWater);
                            } catch (RuntimeException ignored) {
                                block.setType(Material.WATER);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Cleans up all cached snapshots on shutdown.
     */
    public void cleanUp() {
        poolSnapshots.clear();
    }
}
