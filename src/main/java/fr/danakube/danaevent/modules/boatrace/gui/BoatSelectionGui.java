package fr.danakube.danaevent.modules.boatrace.gui;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.gui.CustomGui;
import fr.danakube.danaevent.modules.boatrace.manager.BoatSkinManager;
import fr.danakube.danaevent.modules.boatrace.model.BoatSkin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Interactive 36-slot CustomGui for selecting cosmetic boat models.
 * Highlights current active boat, displays lock badges for restricted boats,
 * and triggers optional onSelect callback upon choice.
 */
public class BoatSelectionGui {

    public static final int SIZE = 36;
    public static final int CLOSE_SLOT = 31;

    private static final int[] CENTER_10_SLOTS = {
        11, 12, 13, 14, 15,
        20, 21, 22, 23, 24
    };

    private static final int[] ALL_CENTER_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25
    };

    private final DanaEventPlugin plugin;
    private final BoatSkinManager boatSkinManager;
    private final Consumer<Material> onSelect;
    private final CustomGui customGui;

    public BoatSelectionGui(@NotNull DanaEventPlugin plugin, @NotNull BoatSkinManager boatSkinManager) {
        this(plugin, boatSkinManager, null);
    }

    public BoatSelectionGui(@NotNull DanaEventPlugin plugin, @NotNull BoatSkinManager boatSkinManager, @Nullable Consumer<Material> onSelect) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.boatSkinManager = Objects.requireNonNull(boatSkinManager, "boatSkinManager cannot be null");
        this.onSelect = onSelect;

        Component title = MiniMessage.miniMessage().deserialize(
            "<gradient:#00c6ff:#0072ff><bold>Choix du Bateau</bold></gradient>"
        );
        this.customGui = new CustomGui(title, SIZE);
    }

    public CustomGui getCustomGui() {
        return customGui;
    }

    /**
     * Builds the items in the GUI adapted for the given player and opens it.
     *
     * @param player player opening the GUI
     */
    public void open(@NotNull Player player) {
        Objects.requireNonNull(player, "player cannot be null");

        render(player);

        if (plugin.getGuiManager() != null) {
            plugin.getGuiManager().openGui(player, customGui);
        } else {
            player.openInventory(customGui.getInventory());
        }
    }

    /**
     * Renders background borders, boat item models, and close button for the player.
     *
     * @param player player to render for
     */
    public void render(@NotNull Player player) {
        customGui.clear();

        // 1. Decorative border
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.displayName(CustomGui.textWithoutItalic(""));
            border.setItemMeta(borderMeta);
        }
        customGui.fillBorder(border);

        // 2. Close button at bottom center
        ItemStack closeBtn = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeBtn.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(CustomGui.textWithoutItalic("<red><bold>Fermer</bold></red>"));
            closeMeta.lore(List.of(CustomGui.textWithoutItalic("<gray>Cliquez pour fermer l'inventaire.</gray>")));
            closeBtn.setItemMeta(closeMeta);
        }
        customGui.setItem(CLOSE_SLOT, closeBtn, event -> player.closeInventory());

        // 3. Render available boat cosmetics
        List<BoatSkin> skins = boatSkinManager.getAvailableSkins();
        int[] targetSlots = skins.size() <= 10 ? CENTER_10_SLOTS : ALL_CENTER_SLOTS;

        Material currentPreferred = boatSkinManager.getPreference(player.getUniqueId());

        int count = Math.min(skins.size(), targetSlots.length);
        for (int i = 0; i < count; i++) {
            BoatSkin skin = skins.get(i);
            int slot = targetSlots[i];

            boolean hasPerm = skin.hasPermission(player);
            boolean isCurrent = hasPerm && currentPreferred != null && currentPreferred == skin.material();

            ItemStack boatItem = new ItemStack(skin.material());
            ItemMeta meta = boatItem.getItemMeta();

            if (meta != null) {
                meta.displayName(CustomGui.textWithoutItalic(skin.name()));

                List<Component> lore = new ArrayList<>();
                lore.add(CustomGui.textWithoutItalic(""));

                if (hasPerm) {
                    if (isCurrent) {
                        lore.add(CustomGui.textWithoutItalic("<green>✔ Bateau Actuel</green>"));
                        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    } else {
                        lore.add(CustomGui.textWithoutItalic("<gray>Cliquez pour choisir ce bateau.</gray>"));
                    }
                } else {
                    lore.add(CustomGui.textWithoutItalic("<red>🔒 Verrouillé</red>"));
                }

                meta.lore(lore);
                boatItem.setItemMeta(meta);
            }

            customGui.setItem(slot, boatItem, event -> {
                if (!hasPerm) {
                    plugin.getMessageManager().sendMessage(player, "boatrace-boat-locked");
                    return;
                }

                if (isCurrent) {
                    // Re-confirming active boat
                    player.closeInventory();
                    if (onSelect != null) {
                        onSelect.accept(skin.material());
                    }
                    return;
                }

                // Apply new preference
                boatSkinManager.setPreference(player.getUniqueId(), skin.material());
                plugin.getMessageManager().sendMessage(
                    player,
                    "boatrace-boat-selected",
                    Placeholder.component("boat", MiniMessage.miniMessage().deserialize(skin.name()))
                );
                player.closeInventory();

                if (onSelect != null) {
                    onSelect.accept(skin.material());
                }
            });
        }
    }
}
