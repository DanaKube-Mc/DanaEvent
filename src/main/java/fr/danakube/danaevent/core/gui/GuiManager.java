package fr.danakube.danaevent.core.gui;

import fr.danakube.danaevent.DanaEventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active CustomGui inventories and coordinates lifecycle and clean closure.
 */
public class GuiManager {

    private final DanaEventPlugin plugin;
    private final Map<UUID, CustomGui> openGuis = new ConcurrentHashMap<>();

    public GuiManager(DanaEventPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "DanaEventPlugin cannot be null");
    }

    /**
     * Opens a CustomGui for a player and tracks it.
     *
     * @param player the player
     * @param gui    the custom gui to open
     */
    public void openGui(Player player, CustomGui gui) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(gui, "gui cannot be null");

        openGuis.put(player.getUniqueId(), gui);
        player.openInventory(gui.getInventory());
    }

    /**
     * Closes the GUI currently opened by the player if it is a CustomGui.
     *
     * @param player the player
     */
    public void closeGui(Player player) {
        if (player == null) {
            return;
        }
        openGuis.remove(player.getUniqueId());
        try {
            if (player.getOpenInventory() != null 
                    && player.getOpenInventory().getTopInventory() != null
                    && player.getOpenInventory().getTopInventory().getHolder() instanceof CustomGui) {
                player.closeInventory();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Handles inventory close cleanup for a player UUID.
     *
     * @param playerUuid player UUID
     */
    public void handleClose(UUID playerUuid) {
        if (playerUuid != null) {
            openGuis.remove(playerUuid);
        }
    }

    /**
     * Retrieves the currently open CustomGui for a player UUID.
     *
     * @param uuid player UUID
     * @return Optional containing CustomGui if tracked
     */
    public Optional<CustomGui> getOpenGui(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(openGuis.get(uuid));
    }

    /**
     * Closes all active CustomGui inventories across all online players.
     */
    public void closeAll() {
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player == null) continue;
                try {
                    if (player.getOpenInventory() != null
                            && player.getOpenInventory().getTopInventory() != null
                            && player.getOpenInventory().getTopInventory().getHolder() instanceof CustomGui) {
                        player.closeInventory();
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        openGuis.clear();
    }

    /**
     * Cleans up all open inventories and clears tracked data to prevent memory leaks on shutdown.
     */
    public void cleanUp() {
        closeAll();
        openGuis.clear();
    }

    public DanaEventPlugin getPlugin() {
        return plugin;
    }
}
