package fr.danakube.danaevent.core.hook;

import fr.danakube.danaevent.DanaEventPlugin;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Hook for PlaceholderAPI.
 * Provides transparent fallback without crashes if PlaceholderAPI is not present.
 */
public class PlaceholderAPIHook {

    private final DanaEventPlugin plugin;
    private final boolean available;
    private Object expansion;

    public PlaceholderAPIHook(DanaEventPlugin plugin, boolean available) {
        this.plugin = plugin;
        this.available = available;
        if (this.available) {
            registerExpansion();
        }
    }

    private void registerExpansion() {
        try {
            this.expansion = PapiHandler.register(plugin);
            plugin.getLogger().info("PlaceholderAPI hook registered successfully with identifier 'danaevent'.");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to register PlaceholderAPI expansion: " + t.getMessage(), t);
        }
    }

    /**
     * Checks if PlaceholderAPI is available and active.
     *
     * @return true if available, false otherwise
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Parses placeholders in the given text using PlaceholderAPI.
     * If PlaceholderAPI is unavailable or text is null, returns the original text transparently.
     *
     * @param player the player context (can be null)
     * @param text   the text containing placeholders
     * @return text with parsed placeholders, or original text if unavailable
     */
    public String parsePlaceholders(Player player, String text) {
        if (!available || text == null) {
            return text;
        }
        try {
            return PapiHandler.setPlaceholders(player, text);
        } catch (Throwable t) {
            return text;
        }
    }

    /**
     * Unregisters the PlaceholderAPI expansion cleanly.
     */
    public void unregister() {
        if (expansion != null) {
            try {
                PapiHandler.unregister(expansion);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Error while unregistering PlaceholderAPI expansion: " + t.getMessage(), t);
            } finally {
                expansion = null;
            }
        }
    }

    /**
     * Internal isolated handler to prevent classloader errors when PAPI is absent.
     */
    private static class PapiHandler {
        private static Object register(DanaEventPlugin plugin) {
            DanaEventPlaceholderExpansion exp = new DanaEventPlaceholderExpansion(plugin);
            exp.register();
            return exp;
        }

        private static String setPlaceholders(Player player, String text) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }

        private static void unregister(Object expansion) {
            if (expansion instanceof DanaEventPlaceholderExpansion exp) {
                exp.unregister();
            }
        }
    }
}
