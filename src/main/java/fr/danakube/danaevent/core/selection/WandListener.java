package fr.danakube.danaevent.core.selection;

import fr.danakube.danaevent.DanaEventPlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

/**
 * Listener for player interactions with the selection wand.
 * Handles left-click (Pos1) and right-click (Pos2) on blocks, cancels interactions,
 * prevents block breaking with the wand, and sends feedback messages with MiniMessage.
 */
public class WandListener implements Listener {

    private final DanaEventPlugin plugin;
    private final SelectionManager selectionManager;

    public WandListener(DanaEventPlugin plugin, SelectionManager selectionManager) {
        this.plugin = Objects.requireNonNull(plugin, "DanaEventPlugin cannot be null");
        this.selectionManager = Objects.requireNonNull(selectionManager, "SelectionManager cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!selectionManager.isWand(item)) {
            return;
        }

        // Always cancel wand events to prevent placing or interacting with blocks
        event.setCancelled(true);

        // Process only main hand interactions to prevent duplicate execution
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Location loc = clickedBlock.getLocation();
        Action action = event.getAction();

        if (action == Action.LEFT_CLICK_BLOCK) {
            selectionManager.setPos1(uuid, loc);
            sendPosSetMessage(player, "wand-pos1-set", loc);
            checkAndSendVolume(player, uuid);
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            selectionManager.setPos2(uuid, loc);
            sendPosSetMessage(player, "wand-pos2-set", loc);
            checkAndSendVolume(player, uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (selectionManager.isWand(mainHand) || selectionManager.isWand(offHand)) {
            event.setCancelled(true);
        }
    }

    private void sendPosSetMessage(Player player, String messageKey, Location loc) {
        if (plugin.getMessageManager() == null) {
            return;
        }
        plugin.getMessageManager().sendMessage(
            player,
            messageKey,
            Placeholder.parsed("x", String.valueOf(loc.getBlockX())),
            Placeholder.parsed("y", String.valueOf(loc.getBlockY())),
            Placeholder.parsed("z", String.valueOf(loc.getBlockZ())),
            Placeholder.parsed("world", loc.getWorld() != null ? loc.getWorld().getName() : "")
        );
    }

    private void checkAndSendVolume(Player player, UUID uuid) {
        if (plugin.getMessageManager() == null) {
            return;
        }
        selectionManager.getRegion(uuid).ifPresent(region -> {
            plugin.getMessageManager().sendMessage(
                player,
                "wand-volume",
                Placeholder.parsed("volume", String.valueOf(region.getVolume())),
                Placeholder.parsed("width_x", String.valueOf(region.getWidthX())),
                Placeholder.parsed("height_y", String.valueOf(region.getHeightY())),
                Placeholder.parsed("width_z", String.valueOf(region.getWidthZ())),
                Placeholder.parsed("world", region.getWorldName())
            );
        });
    }
}
