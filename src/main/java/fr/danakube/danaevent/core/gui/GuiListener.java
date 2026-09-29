package fr.danakube.danaevent.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Listens for inventory interactions and routes them to CustomGui instances.
 * Enforces theft prevention and handles click callbacks.
 */
public class GuiListener implements Listener {

    private final GuiManager guiManager;

    public GuiListener(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    public GuiListener() {
        this(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CustomGui customGui) {
            // Anti-theft protection
            if (customGui.isCancelClicks()) {
                event.setCancelled(true);
            }

            // Check if the clicked inventory is the CustomGui itself
            if (event.getClickedInventory() != null && event.getClickedInventory().getHolder() instanceof CustomGui) {
                int slot = event.getSlot();
                GuiItem item = customGui.getItem(slot);
                if (item != null) {
                    item.handleClick(event);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CustomGui customGui) {
            if (customGui.isCancelClicks()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CustomGui customGui) {
            if (event.getPlayer() instanceof Player player) {
                try {
                    customGui.onClose(player);
                } finally {
                    if (guiManager != null) {
                        guiManager.handleClose(player.getUniqueId());
                    }
                }
            }
        }
    }
}
