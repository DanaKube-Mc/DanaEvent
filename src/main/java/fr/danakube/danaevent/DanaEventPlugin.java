package fr.danakube.danaevent;

import fr.danakube.danaevent.core.database.DatabaseConfig;
import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.message.MessageManager;
import fr.danakube.danaevent.core.player.PlayerCrashRecoveryListener;
import fr.danakube.danaevent.core.player.PlayerStateManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

public class DanaEventPlugin extends JavaPlugin {

    private static DanaEventPlugin instance;
    private DatabaseManager databaseManager;
    private MessageManager messageManager;
    private PlayerStateManager playerStateManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.messageManager = new MessageManager(this);

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

        this.playerStateManager = new PlayerStateManager(this);
        getServer().getPluginManager().registerEvents(
            new PlayerCrashRecoveryListener(this, this.playerStateManager),
            this
        );

        getLogger().info("DanaEvent Core enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (playerStateManager != null) {
            playerStateManager.cleanUp();
            playerStateManager = null;
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
            databaseManager = null;
        }

        this.messageManager = null;

        getLogger().info("DanaEvent Core disabled successfully.");
        instance = null;
    }

    public static DanaEventPlugin getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public PlayerStateManager getPlayerStateManager() {
        return playerStateManager;
    }
}
