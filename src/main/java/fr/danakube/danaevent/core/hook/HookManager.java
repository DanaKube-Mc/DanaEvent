package fr.danakube.danaevent.core.hook;

import fr.danakube.danaevent.DanaEventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Manages integrations with third-party plugins (PlaceholderAPI, DecentHolograms).
 */
public class HookManager {

    private final DanaEventPlugin plugin;
    private final PlaceholderAPIHook placeholderApiHook;
    private final DecentHologramsHook decentHologramsHook;

    public HookManager(DanaEventPlugin plugin) {
        this(
            plugin,
            Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"),
            Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")
        );
    }

    public HookManager(DanaEventPlugin plugin, boolean papiAvailable, boolean dhAvailable) {
        this.plugin = Objects.requireNonNull(plugin, "DanaEventPlugin cannot be null");
        this.placeholderApiHook = new PlaceholderAPIHook(plugin, papiAvailable);
        this.decentHologramsHook = new DecentHologramsHook(plugin, dhAvailable);

        plugin.getLogger().info("HookManager initialized: PlaceholderAPI=" + papiAvailable
            + ", DecentHolograms=" + dhAvailable);
    }

    /**
     * Checks if PlaceholderAPI is available.
     *
     * @return true if PlaceholderAPI is hooked, false otherwise
     */
    public boolean isPlaceholderApiAvailable() {
        return placeholderApiHook != null && placeholderApiHook.isAvailable();
    }

    /**
     * Checks if DecentHolograms is available.
     *
     * @return true if DecentHolograms is hooked, false otherwise
     */
    public boolean isDecentHologramsAvailable() {
        return decentHologramsHook != null && decentHologramsHook.isAvailable();
    }

    /**
     * @return the PlaceholderAPI hook instance
     */
    public PlaceholderAPIHook getPlaceholderApiHook() {
        return placeholderApiHook;
    }

    /**
     * @return the DecentHolograms hook instance
     */
    public DecentHologramsHook getDecentHologramsHook() {
        return decentHologramsHook;
    }

    /**
     * Parses placeholders in text safely using PlaceholderAPI if available,
     * or returns the text unchanged.
     *
     * @param player the player context (can be null)
     * @param text   the text containing placeholders
     * @return parsed text or original text
     */
    public String parsePlaceholders(Player player, String text) {
        if (placeholderApiHook == null) {
            return text;
        }
        return placeholderApiHook.parsePlaceholders(player, text);
    }

    /**
     * Cleans up all hooks and unregisters expansions.
     */
    public void cleanUp() {
        if (placeholderApiHook != null) {
            placeholderApiHook.unregister();
        }
    }
}
