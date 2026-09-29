package fr.danakube.danaevent.core.player;

import fr.danakube.danaevent.DanaEventPlugin;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages player state snapshots, memory caching, database persistence,
 * and anti-duplication restoration.
 */
public class PlayerStateManager {

    private final DanaEventPlugin plugin;
    private final Map<UUID, PlayerStateSnapshot> cache = new ConcurrentHashMap<>();

    public PlayerStateManager(DanaEventPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "DanaEventPlugin cannot be null");
    }

    /**
     * Saves the player's complete state to the database synchronously, caches it in memory,
     * and safely clears the player's inventory and resets their state.
     * <p>
     * Writing to the database is guaranteed before the inventory is cleared to prevent item loss.
     *
     * @param player the player to save and clear
     * @return true if successfully persisted and cleared, false otherwise
     */
    public boolean saveAndClear(Player player) {
        if (player == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        PlayerStateSnapshot snapshot = PlayerStateSnapshot.of(player);
        byte[] data = snapshot.toByteArray();

        // 1. Write to database synchronously before modifying player inventory
        try {
            plugin.getDatabaseManager().saveSnapshot(uuid, data);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to persist snapshot in database for player "
                + player.getName() + " (" + uuid + ")! Aborting inventory clear to avoid item loss.", e);
            return false;
        }

        // 2. Put snapshot into memory cache
        cache.put(uuid, snapshot);

        // 3. Clear inventory, armor, and offhand
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setExtraContents(new ItemStack[1]);

        // 4. Reset player status
        player.setFoodLevel(20);
        player.setSaturation(5.0f);

        var maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHp = maxHealthAttr != null ? maxHealthAttr.getValue() : player.getMaxHealth();
        player.setHealth(maxHp);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setLevel(0);
        player.setExp(0.0f);
        player.setFireTicks(0);

        return true;
    }

    /**
     * Restores the player's state from memory or database, applies it, removes it from cache,
     * and deletes it from the database to strictly prevent duplication.
     *
     * @param player          the player to restore
     * @param restoreLocation whether to teleport the player back to their saved location
     * @return true if snapshot was found and restored, false if no snapshot exists (prevents double restoration)
     */
    public boolean restore(Player player, boolean restoreLocation) {
        if (player == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();

        // 1. Check memory cache first
        PlayerStateSnapshot snapshot = cache.remove(uuid);

        // 2. Fallback to database if not in memory
        if (snapshot == null) {
            try {
                Optional<byte[]> dbData = plugin.getDatabaseManager().loadSnapshot(uuid);
                if (dbData.isPresent()) {
                    snapshot = PlayerStateSnapshot.fromByteArray(dbData.get());
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load snapshot from database for player "
                    + player.getName() + " (" + uuid + ")", e);
                return false;
            }
        }

        // 3. If no snapshot found anywhere, return false (prevents duplicate restore)
        if (snapshot == null) {
            return false;
        }

        // 4. Apply state to player
        snapshot.applyTo(player, restoreLocation);

        // 5. Delete snapshot from database
        try {
            plugin.getDatabaseManager().deleteSnapshot(uuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete snapshot from database after restore for "
                + player.getName() + " (" + uuid + ")", e);
        }

        // Ensure cache is evicted
        cache.remove(uuid);

        return true;
    }

    /**
     * Checks if a snapshot exists in memory cache or database.
     *
     * @param uuid the player's UUID
     * @return true if snapshot exists, false otherwise
     */
    public boolean hasSnapshot(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        if (cache.containsKey(uuid)) {
            return true;
        }
        try {
            return plugin.getDatabaseManager().loadSnapshot(uuid).isPresent();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check snapshot presence in DB for " + uuid, e);
            return false;
        }
    }

    /**
     * Checks if a player has a saved state snapshot.
     *
     * @param uuid the player's UUID
     * @return true if state exists, false otherwise
     */
    public boolean hasState(UUID uuid) {
        return hasSnapshot(uuid);
    }

    /**
     * Retrieves a snapshot for the given UUID from memory cache or database.
     *
     * @param uuid the player's UUID
     * @return Optional containing the snapshot if present
     */
    public Optional<PlayerStateSnapshot> getSnapshot(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }

        PlayerStateSnapshot cached = cache.get(uuid);
        if (cached != null) {
            return Optional.of(cached);
        }

        try {
            Optional<byte[]> data = plugin.getDatabaseManager().loadSnapshot(uuid);
            if (data.isPresent()) {
                PlayerStateSnapshot loaded = PlayerStateSnapshot.fromByteArray(data.get());
                cache.put(uuid, loaded);
                return Optional.of(loaded);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to retrieve snapshot from database for " + uuid, e);
        }

        return Optional.empty();
    }

    /**
     * Clears all cached snapshots from memory to prevent memory leaks on reload/disable.
     */
    public void cleanUp() {
        cache.clear();
    }
}
