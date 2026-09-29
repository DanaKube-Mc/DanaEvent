package fr.danakube.danaevent.core.hook;

import fr.danakube.danaevent.DanaEventPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for DanaEvent.
 * Provides placeholders:
 * - %danaevent_active_modules%
 * - %danaevent_total_modules%
 * - %danaevent_in_event%
 * - %danaevent_version%
 */
public class DanaEventPlaceholderExpansion extends PlaceholderExpansion {

    private final DanaEventPlugin plugin;

    public DanaEventPlaceholderExpansion(DanaEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "danaevent";
    }

    @Override
    public @NotNull String getAuthor() {
        return "DanaKube";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        Player player = offlinePlayer != null && offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
        return onPlaceholderRequest(player, params);
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (params.equalsIgnoreCase("active_modules")) {
            return String.valueOf(plugin.getModuleManager() != null ? plugin.getModuleManager().getEnabledModules().size() : 0);
        }

        if (params.equalsIgnoreCase("total_modules")) {
            return String.valueOf(plugin.getModuleManager() != null ? plugin.getModuleManager().getModules().size() : 0);
        }

        if (params.equalsIgnoreCase("in_event")) {
            if (player == null || plugin.getPlayerStateManager() == null) {
                return "false";
            }
            return String.valueOf(plugin.getPlayerStateManager().hasSnapshot(player.getUniqueId()));
        }

        if (params.equalsIgnoreCase("version")) {
            return plugin.getPluginMeta().getVersion();
        }

        return null;
    }
}
