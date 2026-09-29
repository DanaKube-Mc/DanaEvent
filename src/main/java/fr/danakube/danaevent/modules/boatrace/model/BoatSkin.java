package fr.danakube.danaevent.modules.boatrace.model;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a cosmetic boat type model loaded from boats.yml.
 *
 * @param id unique boat identifier key in config
 * @param material Bukkit boat material
 * @param name styled display name in MiniMessage format
 * @param permission required permission or empty string if open to all
 */
public record BoatSkin(
    @NotNull String id,
    @NotNull Material material,
    @NotNull String name,
    @NotNull String permission
) {
    public BoatSkin {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(material, "material cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(permission, "permission cannot be null");
    }

    /**
     * Checks if a player has permission to select this boat cosmetic.
     *
     * @param player the player to check
     * @return true if permission is blank or player has the permission
     */
    public boolean hasPermission(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        if (permission.isBlank()) {
            return true;
        }
        return player.hasPermission(permission);
    }
}
