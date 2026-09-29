package fr.danakube.danaevent.core.player;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of a player's complete state including inventory, armor,
 * offhand, attributes, stats, active potion effects, gamemode, flight, and location.
 */
public class PlayerStateSnapshot {

    private final UUID uuid;
    private final ItemStack[] inventory;
    private final ItemStack[] armor;
    private final ItemStack[] extra;
    private final double health;
    private final double maxHealth;
    private final int foodLevel;
    private final float saturation;
    private final int level;
    private final float exp;
    private final List<PotionEffect> effects;
    private final GameMode gameMode;
    private final boolean allowFlight;
    private final boolean isFlying;
    private final Location location;
    private final String worldName;

    public PlayerStateSnapshot(
        UUID uuid,
        ItemStack[] inventory,
        ItemStack[] armor,
        ItemStack[] extra,
        double health,
        double maxHealth,
        int foodLevel,
        float saturation,
        int level,
        float exp,
        Collection<PotionEffect> effects,
        GameMode gameMode,
        boolean allowFlight,
        boolean isFlying,
        Location location
    ) {
        this(
            uuid,
            inventory,
            armor,
            extra,
            health,
            maxHealth,
            foodLevel,
            saturation,
            level,
            exp,
            effects,
            gameMode,
            allowFlight,
            isFlying,
            location,
            location != null && location.getWorld() != null ? location.getWorld().getName() : "world"
        );
    }

    public PlayerStateSnapshot(
        UUID uuid,
        ItemStack[] inventory,
        ItemStack[] armor,
        ItemStack[] extra,
        double health,
        double maxHealth,
        int foodLevel,
        float saturation,
        int level,
        float exp,
        Collection<PotionEffect> effects,
        GameMode gameMode,
        boolean allowFlight,
        boolean isFlying,
        Location location,
        String worldName
    ) {
        this.uuid = Objects.requireNonNull(uuid, "UUID cannot be null");
        this.inventory = cloneItemArray(inventory);
        this.armor = cloneItemArray(armor);
        this.extra = cloneItemArray(extra);
        this.health = health;
        this.maxHealth = maxHealth > 0 ? maxHealth : 20.0;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.level = level;
        this.exp = exp;
        this.effects = effects != null ? List.copyOf(effects) : List.of();
        this.gameMode = gameMode != null ? gameMode : GameMode.SURVIVAL;
        this.allowFlight = allowFlight;
        this.isFlying = isFlying;
        this.location = location != null ? location.clone() : null;
        this.worldName = worldName != null ? worldName : (location != null && location.getWorld() != null ? location.getWorld().getName() : "world");
    }

    /**
     * Captures a complete snapshot of the given player's current state.
     *
     * @param player the player to snapshot
     * @return a new PlayerStateSnapshot
     */
    public static PlayerStateSnapshot of(Player player) {
        Objects.requireNonNull(player, "Player cannot be null");

        ItemStack[] storage = player.getInventory().getStorageContents();
        ItemStack[] armor = player.getInventory().getArmorContents();
        ItemStack[] extra = player.getInventory().getExtraContents();

        var maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getBaseValue() : player.getMaxHealth();
        double health = Math.min(player.getHealth(), maxHealth);

        int foodLevel = player.getFoodLevel();
        float saturation = player.getSaturation();
        int level = player.getLevel();
        float exp = player.getExp();

        List<PotionEffect> effects = new ArrayList<>(player.getActivePotionEffects());
        GameMode gameMode = player.getGameMode();
        boolean allowFlight = player.getAllowFlight();
        boolean isFlying = player.isFlying();

        Location loc = player.getLocation().clone();
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";

        return new PlayerStateSnapshot(
            player.getUniqueId(),
            storage,
            armor,
            extra,
            health,
            maxHealth,
            foodLevel,
            saturation,
            level,
            exp,
            effects,
            gameMode,
            allowFlight,
            isFlying,
            loc,
            worldName
        );
    }

    /**
     * Restores the snapshot onto the target player.
     *
     * @param player          the target player
     * @param restoreLocation whether to teleport the player back to the saved location
     */
    public void applyTo(Player player, boolean restoreLocation) {
        Objects.requireNonNull(player, "Player cannot be null");

        // 1. Inventory & equipment
        try {
            player.getInventory().setStorageContents(cloneItemArray(this.inventory));
        } catch (Throwable ignored) {
            if (this.inventory != null) {
                for (int i = 0; i < this.inventory.length; i++) {
                    player.getInventory().setItem(i, this.inventory[i] != null ? this.inventory[i].clone() : null);
                }
            }
        }
        player.getInventory().setArmorContents(cloneItemArray(this.armor));
        player.getInventory().setExtraContents(cloneItemArray(this.extra));

        // 2. Max Health & Health
        var maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(this.maxHealth);
        } else {
            player.setMaxHealth(this.maxHealth);
        }
        double targetHealth = Math.min(Math.max(this.health, 0.1), this.maxHealth);
        player.setHealth(targetHealth);

        // 3. Food & Saturation
        player.setFoodLevel(this.foodLevel);
        player.setSaturation(this.saturation);

        // 4. Level & Exp
        player.setLevel(this.level);
        player.setExp(this.exp);

        // 5. Potion Effects
        for (PotionEffect active : player.getActivePotionEffects()) {
            player.removePotionEffect(active.getType());
        }
        for (PotionEffect effect : this.effects) {
            player.addPotionEffect(effect);
        }

        // 6. GameMode
        if (this.gameMode != null) {
            player.setGameMode(this.gameMode);
        }

        // 7. Flight
        player.setAllowFlight(this.allowFlight);
        if (this.allowFlight) {
            player.setFlying(this.isFlying);
        } else {
            player.setFlying(false);
        }

        // 8. Location with fallback
        if (restoreLocation) {
            Location targetLocation = null;
            World world = null;
            if (this.worldName != null) {
                world = Bukkit.getWorld(this.worldName);
            }
            if (world == null && this.location != null && this.location.getWorld() != null) {
                world = this.location.getWorld();
            }

            if (world != null && this.location != null) {
                targetLocation = new Location(
                    world,
                    this.location.getX(),
                    this.location.getY(),
                    this.location.getZ(),
                    this.location.getYaw(),
                    this.location.getPitch()
                );
            } else {
                List<World> worlds = Bukkit.getWorlds();
                if (!worlds.isEmpty()) {
                    targetLocation = worlds.get(0).getSpawnLocation();
                }
            }

            if (targetLocation != null) {
                player.teleport(targetLocation);
            }
        }
    }

    /**
     * Serializes this snapshot into a byte array.
     *
     * @return binary representation of the snapshot
     */
    public byte[] toByteArray() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {

            dos.writeInt(1); // Version format
            dos.writeLong(uuid.getMostSignificantBits());
            dos.writeLong(uuid.getLeastSignificantBits());

            writeItemArray(dos, inventory);
            writeItemArray(dos, armor);
            writeItemArray(dos, extra);

            dos.writeDouble(health);
            dos.writeDouble(maxHealth);
            dos.writeInt(foodLevel);
            dos.writeFloat(saturation);
            dos.writeInt(level);
            dos.writeFloat(exp);

            dos.writeInt(effects.size());
            for (PotionEffect effect : effects) {
                dos.writeUTF(effect.getType().getKey().toString());
                dos.writeInt(effect.getDuration());
                dos.writeInt(effect.getAmplifier());
                dos.writeBoolean(effect.isAmbient());
                dos.writeBoolean(effect.hasParticles());
                dos.writeBoolean(effect.hasIcon());
            }

            dos.writeUTF(gameMode != null ? gameMode.name() : GameMode.SURVIVAL.name());
            dos.writeBoolean(allowFlight);
            dos.writeBoolean(isFlying);

            dos.writeUTF(worldName != null ? worldName : "");
            if (location != null) {
                dos.writeDouble(location.getX());
                dos.writeDouble(location.getY());
                dos.writeDouble(location.getZ());
                dos.writeFloat(location.getYaw());
                dos.writeFloat(location.getPitch());
            } else {
                dos.writeDouble(0.0);
                dos.writeDouble(0.0);
                dos.writeDouble(0.0);
                dos.writeFloat(0.0f);
                dos.writeFloat(0.0f);
            }

            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize PlayerStateSnapshot", e);
        }
    }

    public byte[] serialize() {
        return toByteArray();
    }

    /**
     * Deserializes a PlayerStateSnapshot from a byte array.
     *
     * @param data binary representation of the snapshot
     * @return deserialized PlayerStateSnapshot
     */
    public static PlayerStateSnapshot fromByteArray(byte[] data) {
        Objects.requireNonNull(data, "Data cannot be null");
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bais)) {

            int version = dis.readInt();
            if (version != 1) {
                throw new IllegalArgumentException("Unsupported PlayerStateSnapshot version: " + version);
            }

            long mostSig = dis.readLong();
            long leastSig = dis.readLong();
            UUID uuid = new UUID(mostSig, leastSig);

            ItemStack[] inventory = readItemArray(dis);
            ItemStack[] armor = readItemArray(dis);
            ItemStack[] extra = readItemArray(dis);

            double health = dis.readDouble();
            double maxHealth = dis.readDouble();
            int foodLevel = dis.readInt();
            float saturation = dis.readFloat();
            int level = dis.readInt();
            float exp = dis.readFloat();

            int effectCount = dis.readInt();
            List<PotionEffect> effects = new ArrayList<>(effectCount);
            for (int i = 0; i < effectCount; i++) {
                String effectKey = dis.readUTF();
                int duration = dis.readInt();
                int amplifier = dis.readInt();
                boolean ambient = dis.readBoolean();
                boolean particles = dis.readBoolean();
                boolean icon = dis.readBoolean();

                NamespacedKey key = NamespacedKey.fromString(effectKey);
                PotionEffectType type = key != null ? PotionEffectType.getByKey(key) : null;
                if (type == null) {
                    type = PotionEffectType.getByName(effectKey);
                }
                if (type != null) {
                    effects.add(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
                }
            }

            String gameModeName = dis.readUTF();
            GameMode gameMode = GameMode.valueOf(gameModeName);

            boolean allowFlight = dis.readBoolean();
            boolean isFlying = dis.readBoolean();

            String worldName = dis.readUTF();
            double x = dis.readDouble();
            double y = dis.readDouble();
            double z = dis.readDouble();
            float yaw = dis.readFloat();
            float pitch = dis.readFloat();

            World world = !worldName.isEmpty() ? Bukkit.getWorld(worldName) : null;
            Location location = new Location(world, x, y, z, yaw, pitch);

            return new PlayerStateSnapshot(
                uuid,
                inventory,
                armor,
                extra,
                health,
                maxHealth,
                foodLevel,
                saturation,
                level,
                exp,
                effects,
                gameMode,
                allowFlight,
                isFlying,
                location,
                worldName
            );
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to deserialize PlayerStateSnapshot", e);
        }
    }

    public static PlayerStateSnapshot deserialize(byte[] data) {
        return fromByteArray(data);
    }

    private static void writeItemArray(DataOutputStream dos, ItemStack[] items) throws IOException {
        if (items == null) {
            dos.writeInt(0);
            return;
        }
        dos.writeInt(items.length);
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                dos.writeBoolean(false);
            } else {
                dos.writeBoolean(true);
                dos.writeInt(item.getAmount());
                byte[] itemBytes = item.serializeAsBytes();
                dos.writeInt(itemBytes.length);
                dos.write(itemBytes);
            }
        }
    }

    private static ItemStack[] readItemArray(DataInputStream dis) throws IOException {
        int length = dis.readInt();
        ItemStack[] items = new ItemStack[length];
        for (int i = 0; i < length; i++) {
            boolean hasItem = dis.readBoolean();
            if (hasItem) {
                int amount = dis.readInt();
                int byteLength = dis.readInt();
                byte[] bytes = new byte[byteLength];
                dis.readFully(bytes);
                ItemStack item = ItemStack.deserializeBytes(bytes);
                item.setAmount(amount);
                items[i] = item;
            } else {
                items[i] = null;
            }
        }
        return items;
    }

    private static ItemStack[] cloneItemArray(ItemStack[] original) {
        if (original == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            copy[i] = original[i] != null ? original[i].clone() : null;
        }
        return copy;
    }

    public UUID getUuid() {
        return uuid;
    }

    public ItemStack[] getInventory() {
        return cloneItemArray(inventory);
    }

    public ItemStack[] getArmor() {
        return cloneItemArray(armor);
    }

    public ItemStack[] getExtra() {
        return cloneItemArray(extra);
    }

    public double getHealth() {
        return health;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public int getFoodLevel() {
        return foodLevel;
    }

    public float getSaturation() {
        return saturation;
    }

    public int getLevel() {
        return level;
    }

    public float getExp() {
        return exp;
    }

    public Collection<PotionEffect> getEffects() {
        return Collections.unmodifiableList(effects);
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public boolean isAllowFlight() {
        return allowFlight;
    }

    public boolean isFlying() {
        return isFlying;
    }

    public Location getLocation() {
        return location != null ? location.clone() : null;
    }

    public String getWorldName() {
        return worldName;
    }
}
