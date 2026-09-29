package fr.danakube.danaevent.core.selection;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages spatial selections (pos1 and pos2) for players and provides the selection wand item.
 * Selections are kept in memory and cleaned up on disable.
 */
public class SelectionManager {

    public static final String WAND_KEY_NAME = "selection_wand";
    public static final NamespacedKey WAND_KEY = new NamespacedKey("danaevent", WAND_KEY_NAME);

    private final JavaPlugin plugin;
    private final NamespacedKey wandKey;
    private final Map<UUID, Location> pos1Map = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pos2Map = new ConcurrentHashMap<>();

    public SelectionManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.wandKey = new NamespacedKey(plugin, WAND_KEY_NAME);
    }

    /**
     * Creates a new selection wand item tagged with PDC data.
     *
     * @return the wand ItemStack
     */
    public ItemStack createWandItem() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            MiniMessage mm = MiniMessage.miniMessage();
            meta.displayName(mm.deserialize("<!italic><gradient:#00c6ff:#0072ff><b>Bâton de Sélection</b></gradient>"));
            meta.lore(List.of(
                mm.deserialize("<!italic><gray>Clic gauche sur un bloc : </gray><aqua>Définir Pos1</aqua>"),
                mm.deserialize("<!italic><gray>Clic droit sur un bloc : </gray><aqua>Définir Pos2</aqua>")
            ));
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    /**
     * Checks if the given ItemStack is a valid selection wand.
     * Uses PersistentDataContainer check to avoid false positives (e.g. anvil-renamed items).
     *
     * @param item the item to test
     * @return true if the item carries the selection wand PDC tag
     */
    public boolean isWand(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte tag = meta.getPersistentDataContainer().get(wandKey, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }

    /**
     * Gives the selection wand item directly to a player's inventory.
     *
     * @param player the player
     */
    public void giveWand(Player player) {
        Objects.requireNonNull(player, "player cannot be null");
        player.getInventory().addItem(createWandItem());
    }

    /**
     * Sets position 1 for a player.
     *
     * @param uuid the player's UUID
     * @param loc  the Location to set (cloned), or null to remove
     */
    public void setPos1(UUID uuid, Location loc) {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        if (loc == null) {
            pos1Map.remove(uuid);
        } else {
            pos1Map.put(uuid, loc.clone());
        }
    }

    /**
     * Sets position 2 for a player.
     *
     * @param uuid the player's UUID
     * @param loc  the Location to set (cloned), or null to remove
     */
    public void setPos2(UUID uuid, Location loc) {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        if (loc == null) {
            pos2Map.remove(uuid);
        } else {
            pos2Map.put(uuid, loc.clone());
        }
    }

    /**
     * Gets position 1 for a player.
     *
     * @param uuid the player's UUID
     * @return the Location (cloned), or null if not set
     */
    public Location getPos1(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Location loc = pos1Map.get(uuid);
        return loc != null ? loc.clone() : null;
    }

    /**
     * Gets position 2 for a player.
     *
     * @param uuid the player's UUID
     * @return the Location (cloned), or null if not set
     */
    public Location getPos2(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Location loc = pos2Map.get(uuid);
        return loc != null ? loc.clone() : null;
    }

    /**
     * Gets the full cuboid region for a player if both Pos1 and Pos2 are defined and in the same world.
     *
     * @param uuid the player's UUID
     * @return an Optional containing the CuboidRegion, or empty if incomplete or worlds differ
     */
    public Optional<CuboidRegion> getRegion(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        Location p1 = pos1Map.get(uuid);
        Location p2 = pos2Map.get(uuid);
        if (p1 == null || p2 == null || p1.getWorld() == null || p2.getWorld() == null) {
            return Optional.empty();
        }
        if (!p1.getWorld().getName().equals(p2.getWorld().getName())) {
            return Optional.empty();
        }
        return Optional.of(new CuboidRegion(p1, p2));
    }

    /**
     * Clears both Pos1 and Pos2 for a specific player.
     *
     * @param uuid the player's UUID
     */
    public void clearSelection(UUID uuid) {
        if (uuid != null) {
            pos1Map.remove(uuid);
            pos2Map.remove(uuid);
        }
    }

    /**
     * Cleans up all stored selections. Should be called on plugin disable.
     */
    public void cleanUp() {
        pos1Map.clear();
        pos2Map.clear();
    }

    public NamespacedKey getWandKey() {
        return wandKey;
    }
}
