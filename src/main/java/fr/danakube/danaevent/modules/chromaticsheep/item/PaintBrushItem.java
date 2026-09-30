package fr.danakube.danaevent.modules.chromaticsheep.item;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Encapsulates the PaintBrush hotbar item used to dye individual sheep.
 */
public final class PaintBrushItem {

    public static final NamespacedKey KEY_BRUSH = new NamespacedKey("danaevent", "mc_paintbrush");

    private PaintBrushItem() {
    }

    /**
     * Creates a new PaintBrush ItemStack with PDC tag and MiniMessage formatting.
     */
    public static @NotNull ItemStack createItem() {
        ItemStack item = new ItemStack(Material.BRUSH);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MiniMessage.miniMessage().deserialize("<!italic><gradient:#ff5555:#ffff55><b>Pinceau Magique</b></gradient>"));
            meta.lore(List.of(
                MiniMessage.miniMessage().deserialize("<!italic><gray>Faites un clic droit sur un mouton</gray>"),
                MiniMessage.miniMessage().deserialize("<!italic><gray>pour le teindre à votre couleur !</gray>")
            ));
            meta.getPersistentDataContainer().set(KEY_BRUSH, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Checks if the given ItemStack is a valid PaintBrush.
     */
    public static boolean isPaintBrush(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(KEY_BRUSH, PersistentDataType.BYTE);
    }
}
