package fr.danakube.danaevent.modules.chromaticsheep.listener;

import fr.danakube.danaevent.modules.chromaticsheep.item.PaintBombItem;
import fr.danakube.danaevent.modules.chromaticsheep.item.PaintBrushItem;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepData;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Ensures absolute invulnerability and protection for game sheep, and locks hotbar tools.
 */
public class SheepProtectionListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Sheep sheep && SheepData.isGameSheep(sheep)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerShear(@NotNull PlayerShearEntityEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Sheep sheep && SheepData.isGameSheep(sheep)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity instanceof Sheep sheep && SheepData.isGameSheep(sheep)) {
            Player player = event.getPlayer();
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();

            // Prevent feeding wheat or breeding items
            if (isFoodOrShears(mainHand) || isFoodOrShears(offHand)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (PaintBrushItem.isPaintBrush(item) || PaintBombItem.isPaintBomb(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (PaintBrushItem.isPaintBrush(current) || PaintBombItem.isPaintBomb(current)
            || PaintBrushItem.isPaintBrush(cursor) || PaintBombItem.isPaintBomb(cursor)) {
            event.setCancelled(true);
        }
    }

    private boolean isFoodOrShears(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        Material mat = item.getType();
        return mat == Material.WHEAT || mat == Material.SHEARS || mat == Material.NAME_TAG;
    }
}
