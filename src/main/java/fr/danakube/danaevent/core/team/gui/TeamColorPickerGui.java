package fr.danakube.danaevent.core.team.gui;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.gui.CustomGui;
import fr.danakube.danaevent.core.team.manager.TeamManager;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
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
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Interactive 54-slot CustomGui for team captains to select an exclusive team color.
 * Displays available colors as wool blocks, reserved colors as locked glass panes,
 * and current color with an enchanted glint badge.
 */
public class TeamColorPickerGui {

    public static final int SIZE = 54;
    public static final int CLOSE_SLOT = 49;

    public static final int[] COLOR_SLOTS = {
        10, 12, 14, 16,
        19, 21, 23, 25,
        28, 30, 32, 34,
        37, 39, 41, 43
    };

    private final DanaEventPlugin plugin;
    private final TeamManager teamManager;
    private final Consumer<TeamColor> onSelect;
    private final CustomGui customGui;

    public TeamColorPickerGui(@NotNull DanaEventPlugin plugin, @NotNull TeamManager teamManager) {
        this(plugin, teamManager, null);
    }

    public TeamColorPickerGui(
        @NotNull DanaEventPlugin plugin,
        @NotNull TeamManager teamManager,
        @Nullable Consumer<TeamColor> onSelect
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.teamManager = Objects.requireNonNull(teamManager, "teamManager cannot be null");
        this.onSelect = onSelect;

        Component title = MiniMessage.miniMessage().deserialize(
            "<gradient:#00c6ff:#0072ff><bold>Couleur de l'Équipe</bold></gradient>"
        );
        this.customGui = new CustomGui(title, SIZE);
    }

    public @NotNull CustomGui getCustomGui() {
        return customGui;
    }

    /**
     * Opens the color selection GUI for a team leader.
     *
     * @param player player opening the GUI (must be team leader)
     * @param team   the player's team
     */
    public void open(@NotNull Player player, @NotNull DanaTeam team) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(team, "team cannot be null");

        if (!team.isLeader(player.getUniqueId())) {
            plugin.getMessageManager().sendMessage(player, "team-not-leader");
            return;
        }

        render(player, team);

        if (plugin.getGuiManager() != null) {
            plugin.getGuiManager().openGui(player, customGui);
        } else {
            player.openInventory(customGui.getInventory());
        }
    }

    /**
     * Renders background borders, 16 color items, and the close button.
     *
     * @param player player to render for
     * @param team   the player's team
     */
    public void render(@NotNull Player player, @NotNull DanaTeam team) {
        customGui.clear();

        // 1. Decorative border
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.displayName(CustomGui.textWithoutItalic(""));
            border.setItemMeta(borderMeta);
        }
        customGui.fill(border);

        // 2. Close button at bottom center
        ItemStack closeBtn = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeBtn.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(CustomGui.textWithoutItalic("<red><bold>Fermer</bold></red>"));
            closeMeta.lore(List.of(CustomGui.textWithoutItalic("<gray>Cliquez pour fermer l'inventaire.</gray>")));
            closeBtn.setItemMeta(closeMeta);
        }
        customGui.setItem(CLOSE_SLOT, closeBtn, event -> player.closeInventory());

        // 3. Render 16 Minecraft colors
        TeamColor[] colors = TeamColor.values();
        for (int i = 0; i < colors.length && i < COLOR_SLOTS.length; i++) {
            TeamColor color = colors[i];
            int slot = COLOR_SLOTS[i];

            boolean isCurrent = team.getColor() == color;
            Optional<DanaTeam> occupyingTeam = teamManager.getTeamByColor(color);
            boolean isTaken = occupyingTeam.isPresent() && !occupyingTeam.get().getId().equalsIgnoreCase(team.getId());

            ItemStack item;
            ItemMeta meta;

            if (isCurrent) {
                item = new ItemStack(color.getWoolMaterial());
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(CustomGui.textWithoutItalic(color.getDisplayName()));
                    List<Component> lore = new ArrayList<>();
                    lore.add(CustomGui.textWithoutItalic(""));
                    lore.add(CustomGui.textWithoutItalic("<green>✔ Couleur Actuelle</green>"));
                    meta.lore(lore);
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    item.setItemMeta(meta);
                }
                customGui.setItem(slot, item, e -> {
                    // Current color, nothing to change
                });
            } else if (isTaken) {
                DanaTeam other = occupyingTeam.get();
                item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(CustomGui.textWithoutItalic("<dark_gray>" + color.getDisplayName() + "</dark_gray>"));
                    List<Component> lore = new ArrayList<>();
                    lore.add(CustomGui.textWithoutItalic(""));
                    lore.add(CustomGui.textWithoutItalic("<red>🔒 Déjà prise par l'équipe " + other.getDisplayName() + "</red>"));
                    meta.lore(lore);
                    item.setItemMeta(meta);
                }
                customGui.setItem(slot, item, e -> {
                    plugin.getMessageManager().sendMessage(
                        player,
                        "team-color-taken",
                        Placeholder.parsed("color", color.getDisplayName())
                    );
                });
            } else {
                // Available
                item = new ItemStack(color.getWoolMaterial());
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(CustomGui.textWithoutItalic(color.getDisplayName()));
                    List<Component> lore = new ArrayList<>();
                    lore.add(CustomGui.textWithoutItalic(""));
                    lore.add(CustomGui.textWithoutItalic("<gray>Cliquez pour choisir cette couleur.</gray>"));
                    meta.lore(lore);
                    item.setItemMeta(meta);
                }
                customGui.setItem(slot, item, e -> {
                    teamManager.changeTeamColor(team.getId(), color).thenAccept(success -> {
                        if (success) {
                            if (onSelect != null) {
                                onSelect.accept(color);
                            }
                            player.closeInventory();
                        } else {
                            plugin.getMessageManager().sendMessage(
                                player,
                                "team-color-taken",
                                Placeholder.parsed("color", color.getDisplayName())
                            );
                            render(player, team);
                        }
                    });
                });
            }
        }
    }
}
