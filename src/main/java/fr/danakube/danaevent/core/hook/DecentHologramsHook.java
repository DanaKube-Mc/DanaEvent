package fr.danakube.danaevent.core.hook;

import fr.danakube.danaevent.DanaEventPlugin;

/**
 * Optional wrapper for DecentHolograms integration.
 */
public class DecentHologramsHook {

    private final DanaEventPlugin plugin;
    private final boolean available;

    public DecentHologramsHook(DanaEventPlugin plugin, boolean available) {
        this.plugin = plugin;
        this.available = available;
    }

    /**
     * Checks if DecentHolograms is available on the server.
     *
     * @return true if DecentHolograms is enabled, false otherwise
     */
    public boolean isAvailable() {
        return available;
    }

    public DanaEventPlugin getPlugin() {
        return plugin;
    }
}
