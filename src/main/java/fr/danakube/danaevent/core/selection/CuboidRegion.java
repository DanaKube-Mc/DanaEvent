package fr.danakube.danaevent.core.selection;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.SerializableAs;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a 3D cuboid region defined by two opposite corner coordinates in a world.
 * Automatically computes minimum and maximum bounds.
 * Implements Bukkit's {@link ConfigurationSerializable} for YAML persistence.
 */
@SerializableAs("CuboidRegion")
public class CuboidRegion implements ConfigurationSerializable {

    static {
        ConfigurationSerialization.registerClass(CuboidRegion.class, "CuboidRegion");
    }

    private final String worldName;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    /**
     * Constructs a CuboidRegion from two Bukkit locations.
     * Both locations must be non-null and belong to the same world.
     *
     * @param loc1 first corner location
     * @param loc2 second corner location
     * @throws NullPointerException     if either location is null
     * @throws IllegalArgumentException if worlds are null or do not match
     */
    public CuboidRegion(Location loc1, Location loc2) {
        Objects.requireNonNull(loc1, "loc1 cannot be null");
        Objects.requireNonNull(loc2, "loc2 cannot be null");

        if (loc1.getWorld() == null || loc2.getWorld() == null) {
            throw new IllegalArgumentException("Both locations must have a valid world");
        }
        if (!loc1.getWorld().getName().equals(loc2.getWorld().getName())) {
            throw new IllegalArgumentException("Both locations must belong to the same world");
        }

        this.worldName = loc1.getWorld().getName();
        this.minX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        this.minY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        this.minZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());
        this.maxX = Math.max(loc1.getBlockX(), loc2.getBlockX());
        this.maxY = Math.max(loc1.getBlockY(), loc2.getBlockY());
        this.maxZ = Math.max(loc1.getBlockZ(), loc2.getBlockZ());
    }

    /**
     * Constructs a CuboidRegion from world name and corner coordinates.
     *
     * @param worldName the name of the world
     * @param x1 first X coordinate
     * @param y1 first Y coordinate
     * @param z1 first Z coordinate
     * @param x2 second X coordinate
     * @param y2 second Y coordinate
     * @param z2 second Z coordinate
     * @throws NullPointerException if worldName is null
     */
    public CuboidRegion(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.worldName = Objects.requireNonNull(worldName, "worldName cannot be null");
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    /**
     * Deserialization constructor required by Bukkit {@link ConfigurationSerializable}.
     *
     * @param map the serialized map of properties
     */
    public CuboidRegion(Map<String, Object> map) {
        this(
            (String) (map.containsKey("world") ? map.get("world") : map.get("worldName")),
            ((Number) (map.containsKey("minX") ? map.get("minX") : map.get("x1"))).intValue(),
            ((Number) (map.containsKey("minY") ? map.get("minY") : map.get("y1"))).intValue(),
            ((Number) (map.containsKey("minZ") ? map.get("minZ") : map.get("z1"))).intValue(),
            ((Number) (map.containsKey("maxX") ? map.get("maxX") : map.get("x2"))).intValue(),
            ((Number) (map.containsKey("maxY") ? map.get("maxY") : map.get("y2"))).intValue(),
            ((Number) (map.containsKey("maxZ") ? map.get("maxZ") : map.get("z2"))).intValue()
        );
    }

    /**
     * Checks if a Bukkit location is contained within this cuboid region.
     *
     * @param loc the location to test
     * @return true if loc is within bounds and in the same world, false otherwise
     */
    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        if (!worldName.equals(loc.getWorld().getName())) {
            return false;
        }
        return contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Checks if block coordinates are contained within this cuboid's boundaries.
     *
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @return true if the coordinates are inside or on the boundaries
     */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    /**
     * Computes the total number of blocks contained within this region.
     *
     * @return the volume in blocks
     */
    public long getVolume() {
        long widthX = (long) maxX - minX + 1;
        long heightY = (long) maxY - minY + 1;
        long widthZ = (long) maxZ - minZ + 1;
        return widthX * heightY * widthZ;
    }

    /**
     * Gets the geometric center of the region.
     * Adds 0.5 to coordinates to center inside the block.
     *
     * @param world the world to construct the Location in
     * @return the center Location
     * @throws NullPointerException     if world is null
     * @throws IllegalArgumentException if world does not match region's worldName
     */
    public Location getCenter(World world) {
        validateWorld(world);
        double centerX = (minX + maxX) / 2.0 + 0.5;
        double centerY = (minY + maxY) / 2.0 + 0.5;
        double centerZ = (minZ + maxZ) / 2.0 + 0.5;
        return new Location(world, centerX, centerY, centerZ);
    }

    /**
     * Gets the minimum bounding location.
     *
     * @param world the world to construct the Location in
     * @return the minimum Location
     * @throws NullPointerException     if world is null
     * @throws IllegalArgumentException if world does not match region's worldName
     */
    public Location getMinLocation(World world) {
        validateWorld(world);
        return new Location(world, minX, minY, minZ);
    }

    /**
     * Gets the maximum bounding location.
     *
     * @param world the world to construct the Location in
     * @return the maximum Location
     * @throws NullPointerException     if world is null
     * @throws IllegalArgumentException if world does not match region's worldName
     */
    public Location getMaxLocation(World world) {
        validateWorld(world);
        return new Location(world, maxX, maxY, maxZ);
    }

    private void validateWorld(World world) {
        Objects.requireNonNull(world, "world cannot be null");
        if (!world.getName().equals(worldName)) {
            throw new IllegalArgumentException("Provided world '" + world.getName() + "' does not match region world '" + worldName + "'");
        }
    }

    public String getWorldName() {
        return worldName;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public int getWidthX() {
        return maxX - minX + 1;
    }

    public int getHeightY() {
        return maxY - minY + 1;
    }

    public int getWidthZ() {
        return maxZ - minZ + 1;
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", worldName);
        map.put("minX", minX);
        map.put("minY", minY);
        map.put("minZ", minZ);
        map.put("maxX", maxX);
        map.put("maxY", maxY);
        map.put("maxZ", maxZ);
        return map;
    }

    public static CuboidRegion deserialize(Map<String, Object> map) {
        return new CuboidRegion(map);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CuboidRegion that = (CuboidRegion) o;
        return minX == that.minX &&
            minY == that.minY &&
            minZ == that.minZ &&
            maxX == that.maxX &&
            maxY == that.maxY &&
            maxZ == that.maxZ &&
            Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public String toString() {
        return "CuboidRegion{" +
            "world='" + worldName + '\'' +
            ", min=(" + minX + ", " + minY + ", " + minZ + ")" +
            ", max=(" + maxX + ", " + maxY + ", " + maxZ + ")" +
            ", volume=" + getVolume() +
            '}';
    }
}
