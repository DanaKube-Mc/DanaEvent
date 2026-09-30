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
 * Encapsulates the PaintBomb hotbar item used to dye sheep in an area of effect.
 */
public final class PaintBombItem {

    public static final NamespacedKey KEY_BOMB = new NamespacedKey("danaevent", "mc_paintbomb");

    private PaintBombItem() {
    }

    /**
     * Creates a new PaintBomb ItemStack with PDC tag and MiniMessage formatting.
     */
    public static @NotNull ItemStack createItem() {
        ItemStack item = new ItemStack(Material.SNOWBALL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MiniMessage.miniMessage().deserialize("<!italic><gradient:#ff007f:#7928ca><b>Bombe de Peinture</b></gradient>"));
            meta.lore(List.of(
                MiniMessage.miniMessage().deserialize("<!italic><gray>Lancez pour teindre tous les moutons</gray>"),
                MiniMessage.miniMessage().deserialize("<!italic><gray>dans un rayon de 3.5 blocs !</gray>"),
                MiniMessage.miniMessage().deserialize("<!italic><dark_gray>Temps de recharge : 15s</dark_gray>")
            ));
            meta.getPersistentDataContainer().set(KEY_BOMB, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Checks if the given ItemStack is a valid PaintBomb.
     */
    public static boolean isPaintBomb(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(KEY_BOMB, PersistentDataType.BYTE);
    }
}
