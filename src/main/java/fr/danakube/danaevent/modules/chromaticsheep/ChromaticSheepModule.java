package fr.danakube.danaevent.modules.chromaticsheep;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.module.AbstractDanaModule;
import fr.danakube.danaevent.modules.chromaticsheep.command.SheepAdminCmd;
import fr.danakube.danaevent.modules.chromaticsheep.command.SheepJoinCmd;
import fr.danakube.danaevent.modules.chromaticsheep.command.SheepLeaveCmd;
import fr.danakube.danaevent.modules.chromaticsheep.command.SheepListCmd;
import fr.danakube.danaevent.modules.chromaticsheep.command.SheepTopCmd;
import fr.danakube.danaevent.modules.chromaticsheep.config.ArenaConfig;
import fr.danakube.danaevent.modules.chromaticsheep.database.ChromaticSheepDatabase;
import fr.danakube.danaevent.modules.chromaticsheep.listener.ArenaBoundaryListener;
import fr.danakube.danaevent.modules.chromaticsheep.listener.PaintBombListener;
import fr.danakube.danaevent.modules.chromaticsheep.listener.SheepInteractListener;
import fr.danakube.danaevent.modules.chromaticsheep.listener.SheepProtectionListener;
import fr.danakube.danaevent.modules.chromaticsheep.manager.ArenaManager;
import fr.danakube.danaevent.modules.chromaticsheep.manager.HerdManager;
import fr.danakube.danaevent.modules.chromaticsheep.manager.SheepGameManager;
import fr.danakube.danaevent.modules.chromaticsheep.manager.SheepLeaderboardManager;
import fr.danakube.danaevent.modules.chromaticsheep.manager.SheepScoreManager;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Pluggable DanaEvent module for ChromaticSheep (MoutonChromatique) mini-game.
 */
public class ChromaticSheepModule extends AbstractDanaModule {

    private final DanaEventPlugin plugin;
    private ChromaticSheepDatabase database;
    private ArenaConfig arenaConfig;
    private ArenaManager arenaManager;
    private HerdManager herdManager;
    private SheepScoreManager scoreManager;
    private SheepGameManager gameManager;
    private SheepLeaderboardManager leaderboardManager;

    private SheepInteractListener interactListener;
    private PaintBombListener bombListener;
    private SheepProtectionListener protectionListener;
    private ArenaBoundaryListener boundaryListener;

    public ChromaticSheepModule(@NotNull DanaEventPlugin plugin) {
        super("chromaticsheep", List.of("mc"), "Mouton Chromatique", "1.0.0");
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    public void onEnable() {
        super.onEnable();

        this.database = new ChromaticSheepDatabase(plugin.getDatabaseManager());
        try {
            database.initTables();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize chromaticsheep tables: " + e.getMessage(), e);
        }

        File configFile = new File(plugin.getDataFolder(), "modules/chromaticsheep/arenas.yml");
        this.arenaConfig = new ArenaConfig(configFile);
        this.arenaConfig.loadArenas();
        this.arenaManager = new ArenaManager(this.arenaConfig);

        this.herdManager = new HerdManager();
        this.scoreManager = new SheepScoreManager();
        this.gameManager = new SheepGameManager(
            plugin,
            plugin.getPlayerStateManager(),
            this.arenaManager,
            this.herdManager,
            this.scoreManager,
            this.database,
            plugin.getTeamManager()
        );

        this.leaderboardManager = new SheepLeaderboardManager(plugin, database);
        SheepLeaderboardManager.setInstance(this.leaderboardManager);

        this.interactListener = new SheepInteractListener(herdManager, scoreManager, gameManager::getSession);
        this.bombListener = new PaintBombListener(herdManager, scoreManager, gameManager::getSession);
        this.protectionListener = new SheepProtectionListener();
        this.boundaryListener = new ArenaBoundaryListener(arenaManager, gameManager::getPlayerArena);

        Bukkit.getPluginManager().registerEvents(interactListener, plugin);
        Bukkit.getPluginManager().registerEvents(bombListener, plugin);
        Bukkit.getPluginManager().registerEvents(protectionListener, plugin);
        Bukkit.getPluginManager().registerEvents(boundaryListener, plugin);

        if (getSubCommands().isEmpty()) {
            registerSubCommand(new SheepListCmd(plugin, this));
            registerSubCommand(new SheepJoinCmd(plugin, this));
            registerSubCommand(new SheepLeaveCmd(plugin, this));
            registerSubCommand(new SheepTopCmd(plugin, this));
            registerSubCommand(new SheepAdminCmd(plugin, this));
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();

        if (gameManager != null) {
            gameManager.cleanUpAll();
        }

        if (leaderboardManager != null) {
            leaderboardManager.cleanUp();
            leaderboardManager = null;
        }
        SheepLeaderboardManager.setInstance(null);

        if (interactListener != null) {
            HandlerList.unregisterAll(interactListener);
            interactListener = null;
        }
        if (bombListener != null) {
            HandlerList.unregisterAll(bombListener);
            bombListener = null;
        }
        if (protectionListener != null) {
            HandlerList.unregisterAll(protectionListener);
            protectionListener = null;
        }
        if (boundaryListener != null) {
            HandlerList.unregisterAll(boundaryListener);
            boundaryListener = null;
        }
    }

    @Override
    public void onReload() {
        if (arenaManager != null) {
            arenaManager.loadArenas();
        }
        if (leaderboardManager != null) {
            leaderboardManager.refreshCache();
        }
    }

    public DanaEventPlugin getPlugin() {
        return plugin;
    }

    public ChromaticSheepDatabase getDatabase() {
        return database;
    }

    public ArenaConfig getArenaConfig() {
        return arenaConfig;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public HerdManager getHerdManager() {
        return herdManager;
    }

    public SheepScoreManager getScoreManager() {
        return scoreManager;
    }

    public SheepGameManager getGameManager() {
        return gameManager;
    }

    public SheepLeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }
}
