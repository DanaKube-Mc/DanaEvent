package fr.danakube.danaevent.core.team.manager;

import fr.danakube.danaevent.core.gui.CustomGui;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Manages visual assets for event teams, including leather armor dyeing and team glowing states.
 */
public class TeamVisualManager {

    private static final Set<Material> LEATHER_ARMOR_MATERIALS = Set.of(
        Material.LEATHER_HELMET,
        Material.LEATHER_CHESTPLATE,
        Material.LEATHER_LEGGINGS,
        Material.LEATHER_BOOTS
    );

    /**
     * Creates a leather armor item dyed in the team's official color with italics suppressed.
     *
     * @param piece the leather armor material
     * @param color the team color
     * @return the dyed ItemStack
     * @throws IllegalArgumentException if the material is not a leather armor piece
     */
    public static @NotNull ItemStack createColoredArmorPiece(@NotNull Material piece, @NotNull TeamColor color) {
        Objects.requireNonNull(piece, "piece cannot be null");
        Objects.requireNonNull(color, "color cannot be null");

        if (!LEATHER_ARMOR_MATERIALS.contains(piece)) {
            throw new IllegalArgumentException("Material must be a leather armor piece, got: " + piece);
        }

        ItemStack item = new ItemStack(piece);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(color.getBukkitColor());
            String pieceName = switch (piece) {
                case LEATHER_HELMET -> "Casque";
                case LEATHER_CHESTPLATE -> "Plastron";
                case LEATHER_LEGGINGS -> "Jambières";
                case LEATHER_BOOTS -> "Bottes";
                default -> "Armure";
            };
            Component displayName = CustomGui.textWithoutItalic("<white>" + pieceName + " (" + color.getDisplayName() + "<white>)</white>");
            meta.displayName(displayName);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates a complete 4-piece leather armor set [boots, leggings, chestplate, helmet]
     * dyed in the team's official color.
     *
     * @param color the team color
     * @return an array of 4 ItemStacks representing the armor set
     */
    public static @NotNull ItemStack[] createColoredArmorSet(@NotNull TeamColor color) {
        Objects.requireNonNull(color, "color cannot be null");
        return new ItemStack[] {
            createColoredArmorPiece(Material.LEATHER_BOOTS, color),
            createColoredArmorPiece(Material.LEATHER_LEGGINGS, color),
            createColoredArmorPiece(Material.LEATHER_CHESTPLATE, color),
            createColoredArmorPiece(Material.LEATHER_HELMET, color)
        };
    }

    /**
     * Equips a player with a full leather armor set dyed in the specified team color.
     *
     * @param player the player to equip
     * @param color  the team color
     */
    public void equipTeamArmor(@NotNull Player player, @NotNull TeamColor color) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(color, "color cannot be null");

        player.getInventory().setArmorContents(createColoredArmorSet(color));
    }

    /**
     * Toggles glowing on all online members of a team.
     *
     * @param team    the DanaTeam
     * @param glowing true to enable glowing, false otherwise
     */
    public void setTeamGlowing(@NotNull DanaTeam team, boolean glowing) {
        Objects.requireNonNull(team, "team cannot be null");
        for (UUID memberUuid : team.getMembers().keySet()) {
            Player player = Bukkit.getPlayer(memberUuid);
            if (player != null) {
                player.setGlowing(glowing);
            }
        }
    }
}
