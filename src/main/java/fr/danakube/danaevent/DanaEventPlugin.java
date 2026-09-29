package fr.danakube.danaevent;

import org.bukkit.plugin.java.JavaPlugin;

public class DanaEventPlugin extends JavaPlugin {

    private static DanaEventPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("DanaEvent Core enabled successfully.");
    }

    @Override
    public void onDisable() {
        getLogger().info("DanaEvent Core disabled successfully.");
        instance = null;
    }

    public static DanaEventPlugin getInstance() {
        return instance;
    }
}
