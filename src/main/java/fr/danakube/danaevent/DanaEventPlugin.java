package fr.danakube.danaevent;

import fr.danakube.danaevent.core.command.CommandManager;
import fr.danakube.danaevent.core.database.DatabaseConfig;
import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.gui.GuiListener;
import fr.danakube.danaevent.core.gui.GuiManager;
import fr.danakube.danaevent.core.hook.HookManager;
import fr.danakube.danaevent.core.message.MessageManager;
import fr.danakube.danaevent.core.module.ModuleManager;
import fr.danakube.danaevent.core.player.PlayerCrashRecoveryListener;
import fr.danakube.danaevent.core.player.PlayerStateManager;
import fr.danakube.danaevent.core.selection.SelectionManager;
import fr.danakube.danaevent.core.selection.WandListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

public class DanaEventPlugin extends JavaPlugin {

    private static DanaEventPlugin instance;
    private DatabaseManager databaseManager;
    private MessageManager messageManager;
    private PlayerStateManager playerStateManager;
    private SelectionManager selectionManager;
    private ModuleManager moduleManager;
    private CommandManager commandManager;
    private HookManager hookManager;
    private GuiManager guiManager;

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

        this.selectionManager = new SelectionManager(this);
        getServer().getPluginManager().registerEvents(
            new WandListener(this, this.selectionManager),
            this
        );

        this.moduleManager = new ModuleManager(this);
        this.commandManager = new CommandManager(this);

        this.hookManager = new HookManager(this);
        this.guiManager = new GuiManager(this);
        getServer().getPluginManager().registerEvents(
            new GuiListener(this.guiManager),
            this
        );

        PluginCommand danaeventCmd = getCommand("danaevent");
        if (danaeventCmd != null) {
            danaeventCmd.setExecutor(this.commandManager);
            danaeventCmd.setTabCompleter(this.commandManager);
        } else {
            getLogger().warning("Failed to register /danaevent command: not found in plugin description.");
        }

        this.moduleManager.enableAll();

        getLogger().info("DanaEvent Core enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (guiManager != null) {
            guiManager.cleanUp();
            guiManager = null;
        }

        if (hookManager != null) {
            hookManager.cleanUp();
            hookManager = null;
        }

        if (moduleManager != null) {
            moduleManager.disableAll();
            moduleManager = null;
        }

        if (commandManager != null) {
            commandManager.cleanUp();
            commandManager = null;
        }

        if (selectionManager != null) {
            selectionManager.cleanUp();
            selectionManager = null;
        }

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

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }
}
