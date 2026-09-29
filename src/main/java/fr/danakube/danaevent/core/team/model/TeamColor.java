package fr.danakube.danaevent.core.team.model;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enumeration of the 16 Minecraft team colors with associated display components,
 * Kyori Adventure text colors, Bukkit leather armor colors, and GUI display materials.
 */
public enum TeamColor {

    WHITE(
        "white",
        "<!italic><white>Blanc</white>",
        NamedTextColor.WHITE,
        TextColor.color(0xFFFFFF),
        Color.fromRGB(255, 255, 255),
        Material.WHITE_WOOL,
        Material.WHITE_DYE,
        Material.WHITE_STAINED_GLASS_PANE
    ),
    ORANGE(
        "orange",
        "<!italic><gold>Orange</gold>",
        NamedTextColor.GOLD,
        TextColor.color(0xFFA500),
        Color.fromRGB(249, 128, 29),
        Material.ORANGE_WOOL,
        Material.ORANGE_DYE,
        Material.ORANGE_STAINED_GLASS_PANE
    ),
    MAGENTA(
        "magenta",
        "<!italic><light_purple>Magenta</light_purple>",
        NamedTextColor.LIGHT_PURPLE,
        TextColor.color(0xC74EBD),
        Color.fromRGB(199, 78, 189),
        Material.MAGENTA_WOOL,
        Material.MAGENTA_DYE,
        Material.MAGENTA_STAINED_GLASS_PANE
    ),
    LIGHT_BLUE(
        "light_blue",
        "<!italic><aqua>Bleu Clair</aqua>",
        NamedTextColor.AQUA,
        TextColor.color(0x3AB3DA),
        Color.fromRGB(58, 179, 218),
        Material.LIGHT_BLUE_WOOL,
        Material.LIGHT_BLUE_DYE,
        Material.LIGHT_BLUE_STAINED_GLASS_PANE
    ),
    YELLOW(
        "yellow",
        "<!italic><yellow>Jaune</yellow>",
        NamedTextColor.YELLOW,
        TextColor.color(0xFED83D),
        Color.fromRGB(254, 216, 61),
        Material.YELLOW_WOOL,
        Material.YELLOW_DYE,
        Material.YELLOW_STAINED_GLASS_PANE
    ),
    LIME(
        "lime",
        "<!italic><green>Vert Clair</green>",
        NamedTextColor.GREEN,
        TextColor.color(0x80C71F),
        Color.fromRGB(128, 199, 31),
        Material.LIME_WOOL,
        Material.LIME_DYE,
        Material.LIME_STAINED_GLASS_PANE
    ),
    PINK(
        "pink",
        "<!italic><color:#F38BAA>Rose</color>",
        NamedTextColor.LIGHT_PURPLE,
        TextColor.color(0xF38BAA),
        Color.fromRGB(243, 139, 170),
        Material.PINK_WOOL,
        Material.PINK_DYE,
        Material.PINK_STAINED_GLASS_PANE
    ),
    GRAY(
        "gray",
        "<!italic><dark_gray>Gris</dark_gray>",
        NamedTextColor.DARK_GRAY,
        TextColor.color(0x474F52),
        Color.fromRGB(71, 79, 82),
        Material.GRAY_WOOL,
        Material.GRAY_DYE,
        Material.GRAY_STAINED_GLASS_PANE
    ),
    LIGHT_GRAY(
        "light_gray",
        "<!italic><gray>Gris Clair</gray>",
        NamedTextColor.GRAY,
        TextColor.color(0x9D9D97),
        Color.fromRGB(157, 157, 151),
        Material.LIGHT_GRAY_WOOL,
        Material.LIGHT_GRAY_DYE,
        Material.LIGHT_GRAY_STAINED_GLASS_PANE
    ),
    CYAN(
        "cyan",
        "<!italic><dark_aqua>Cyan</dark_aqua>",
        NamedTextColor.DARK_AQUA,
        TextColor.color(0x169C9C),
        Color.fromRGB(22, 156, 156),
        Material.CYAN_WOOL,
        Material.CYAN_DYE,
        Material.CYAN_STAINED_GLASS_PANE
    ),
    PURPLE(
        "purple",
        "<!italic><dark_purple>Violet</dark_purple>",
        NamedTextColor.DARK_PURPLE,
        TextColor.color(0x8932B8),
        Color.fromRGB(137, 50, 184),
        Material.PURPLE_WOOL,
        Material.PURPLE_DYE,
        Material.PURPLE_STAINED_GLASS_PANE
    ),
    BLUE(
        "blue",
        "<!italic><blue>Bleu</blue>",
        NamedTextColor.BLUE,
        TextColor.color(0x3C44AA),
        Color.fromRGB(60, 68, 170),
        Material.BLUE_WOOL,
        Material.BLUE_DYE,
        Material.BLUE_STAINED_GLASS_PANE
    ),
    BROWN(
        "brown",
        "<!italic><color:#835432>Marron</color>",
        NamedTextColor.GOLD,
        TextColor.color(0x835432),
        Color.fromRGB(131, 84, 50),
        Material.BROWN_WOOL,
        Material.BROWN_DYE,
        Material.BROWN_STAINED_GLASS_PANE
    ),
    GREEN(
        "green",
        "<!italic><dark_green>Vert</dark_green>",
        NamedTextColor.DARK_GREEN,
        TextColor.color(0x5E7C16),
        Color.fromRGB(94, 124, 22),
        Material.GREEN_WOOL,
        Material.GREEN_DYE,
        Material.GREEN_STAINED_GLASS_PANE
    ),
    RED(
        "red",
        "<!italic><red>Rouge</red>",
        NamedTextColor.RED,
        TextColor.color(0xB02E26),
        Color.fromRGB(176, 46, 38),
        Material.RED_WOOL,
        Material.RED_DYE,
        Material.RED_STAINED_GLASS_PANE
    ),
    BLACK(
        "black",
        "<!italic><black>Noir</black>",
        NamedTextColor.BLACK,
        TextColor.color(0x1D1D21),
        Color.fromRGB(29, 29, 33),
        Material.BLACK_WOOL,
        Material.BLACK_DYE,
        Material.BLACK_STAINED_GLASS_PANE
    );

    private final String id;
    private final String displayName;
    private final NamedTextColor namedTextColor;
    private final TextColor textColor;
    private final Color bukkitColor;
    private final Material woolMaterial;
    private final Material dyeMaterial;
    private final Material glassPaneMaterial;

    TeamColor(
        String id,
        String displayName,
        NamedTextColor namedTextColor,
        TextColor textColor,
        Color bukkitColor,
        Material woolMaterial,
        Material dyeMaterial,
        Material glassPaneMaterial
    ) {
        this.id = id;
        this.displayName = displayName;
        this.namedTextColor = namedTextColor;
        this.textColor = textColor;
        this.bukkitColor = bukkitColor;
        this.woolMaterial = woolMaterial;
        this.dyeMaterial = dyeMaterial;
        this.glassPaneMaterial = glassPaneMaterial;
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull String getDisplayName() {
        return displayName;
    }

    public @NotNull NamedTextColor getNamedTextColor() {
        return namedTextColor;
    }

    public @NotNull TextColor getTextColor() {
        return textColor;
    }

    public @NotNull Color getBukkitColor() {
        return bukkitColor;
    }

    public @NotNull Material getWoolMaterial() {
        return woolMaterial;
    }

    public @NotNull Material getDyeMaterial() {
        return dyeMaterial;
    }

    public @NotNull Material getGlassPaneMaterial() {
        return glassPaneMaterial;
    }

    /**
     * Resolves a TeamColor from its unique ID or enum name (case-insensitive).
     *
     * @param input string identifier or enum name
     * @return Optional containing the resolved TeamColor, or empty
     */
    public static @NotNull Optional<TeamColor> fromString(@Nullable String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String clean = input.trim().toLowerCase().replace("-", "_");
        return Arrays.stream(values())
            .filter(c -> c.id.equalsIgnoreCase(clean) || c.name().equalsIgnoreCase(clean))
            .findFirst();
    }
}
