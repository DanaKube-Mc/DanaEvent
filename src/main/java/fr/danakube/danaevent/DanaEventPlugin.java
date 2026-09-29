package fr.danakube.danaevent;

import fr.danakube.danaevent.core.database.DatabaseConfig;
import fr.danakube.danaevent.core.database.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

public class DanaEventPlugin extends JavaPlugin {

    private static DanaEventPlugin instance;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        DatabaseConfig dbConfig = DatabaseConfig.fromConfiguration(
            getConfig().getConfigurationSection("database"),
            "danaevent.db"
        );

        try {
            databaseManager = new DatabaseManager(dbConfig, getDataFolder());
            getLogger().info("Database successfully connected and initialized.");
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to initialize database connection!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("DanaEvent Core enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
            databaseManager = null;
        }

        getLogger().info("DanaEvent Core disabled successfully.");
        instance = null;
    }

    public static DanaEventPlugin getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
