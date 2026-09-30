package fr.danakube.danaevent.modules.deacoudre;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.core.module.AbstractDanaModule;
import fr.danakube.danaevent.modules.deacoudre.command.DacAdminCmd;
import fr.danakube.danaevent.modules.deacoudre.command.DacJoinCmd;
import fr.danakube.danaevent.modules.deacoudre.command.DacLeaveCmd;
import fr.danakube.danaevent.modules.deacoudre.command.DacListCmd;
import fr.danakube.danaevent.modules.deacoudre.command.DacTopCmd;
import fr.danakube.danaevent.modules.deacoudre.config.DacConfig;
import fr.danakube.danaevent.modules.deacoudre.database.DeACoudreDatabase;
import fr.danakube.danaevent.modules.deacoudre.listener.DacDivingPlatformListener;
import fr.danakube.danaevent.modules.deacoudre.listener.DacFallListener;
import fr.danakube.danaevent.modules.deacoudre.listener.DacProtectionListener;
import fr.danakube.danaevent.modules.deacoudre.manager.DacArenaManager;
import fr.danakube.danaevent.modules.deacoudre.manager.DacGameManager;
import fr.danakube.danaevent.modules.deacoudre.manager.DacLeaderboardManager;
import fr.danakube.danaevent.modules.deacoudre.manager.DacPoolManager;
import fr.danakube.danaevent.modules.deacoudre.task.DacHudTask;
import fr.danakube.danaevent.modules.deacoudre.task.JumpCountdownTask;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Micro-Kernel module for the Dé à Coudre mini-game.
 */
public class DeACoudreModule extends AbstractDanaModule {

    private final DanaEventPlugin plugin;
    private final List<SubCommand> subCommands = new ArrayList<>();
    private final List<Listener> registeredListeners = new ArrayList<>();
    private final Map<String, JumpCountdownTask> activeCountdownTasks = new ConcurrentHashMap<>();

    private DacConfig config;
    private DacArenaManager arenaManager;
    private DacPoolManager poolManager;
    private DeACoudreDatabase database;
    private DacGameManager gameManager;
    private DacLeaderboardManager leaderboardManager;
    private DacHudTask hudTask;

    public DeACoudreModule(@NotNull DanaEventPlugin plugin) {
        super("deacoudre", "Dé à Coudre", "1.0.0");
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    public List<String> getAliases() {
        return List.of("dac");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        // 1. Storage & Config
        File moduleDir = new File(plugin.getDataFolder(), "modules/deacoudre");
        if (!moduleDir.exists()) {
            moduleDir.mkdirs();
        }

        File configFile = new File(moduleDir, "dac_arenas.yml");
        this.config = new DacConfig(configFile);
        this.arenaManager = new DacArenaManager(config);
        this.poolManager = new DacPoolManager();

        this.database = new DeACoudreDatabase(plugin.getDatabaseManager());
        try {
            this.database.initTables();
        } catch (java.sql.SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to initialize deacoudre tables: " + e.getMessage(), e);
        }

        this.leaderboardManager = new DacLeaderboardManager(plugin, database);
        DacLeaderboardManager.setInstance(leaderboardManager);

        this.gameManager = new DacGameManager(
            plugin,
            plugin.getPlayerStateManager(),
            arenaManager,
            poolManager,
            database,
            plugin.getTeamManager()
        );

        // 2. Wire Countdown Tasks to Turns
        this.gameManager.setTurnStartListener(game -> {
            String arenaId = game.getArena().getId();
            JumpCountdownTask oldTask = activeCountdownTasks.remove(arenaId);
            if (oldTask != null) {
                oldTask.cancelAndClean();
            }

            JumpCountdownTask newTask = new JumpCountdownTask(
                game,
                gameManager,
                game.getCurrentJumper(),
                game.getArena().getJumpTimeSeconds()
            );
            activeCountdownTasks.put(arenaId, newTask);
            newTask.runTaskTimer(plugin, 20L, 20L);
        });

        this.gameManager.setGameEndListener(game -> {
            JumpCountdownTask oldTask = activeCountdownTasks.remove(game.getArena().getId());
            if (oldTask != null) {
                oldTask.cancelAndClean();
            }
        });

        // 3. Register Listeners
        registerListener(new DacFallListener(poolManager, gameManager::getSession, gameManager::getPlayerArena, gameManager));
        registerListener(new DacProtectionListener(poolManager, gameManager::getSession, arenaManager::getArenas));
        registerListener(new DacDivingPlatformListener(gameManager));

        // 4. Start HUD Task
        this.hudTask = new DacHudTask(gameManager, poolManager);
        this.hudTask.runTaskTimer(plugin, 20L, 20L);

        // 5. Register SubCommands
        this.subCommands.clear();
        this.subCommands.add(new DacListCmd(plugin, this));
        this.subCommands.add(new DacJoinCmd(plugin, this));
        this.subCommands.add(new DacLeaveCmd(plugin, this));
        this.subCommands.add(new DacTopCmd(plugin, this));
        this.subCommands.add(new DacAdminCmd(plugin, this));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        for (JumpCountdownTask task : activeCountdownTasks.values()) {
            task.cancelAndClean();
        }
        activeCountdownTasks.clear();

        if (hudTask != null) {
            try {
                hudTask.cancel();
            } catch (IllegalStateException ignored) {
            }
            hudTask.cleanUp();
            hudTask = null;
        }

        if (gameManager != null) {
            gameManager.cleanUpAll();
        }

        if (poolManager != null) {
            poolManager.cleanUp();
        }

        if (leaderboardManager != null) {
            leaderboardManager.invalidateAll();
            DacLeaderboardManager.setInstance(null);
        }

        for (Listener listener : registeredListeners) {
            HandlerList.unregisterAll(listener);
        }
        registeredListeners.clear();
        subCommands.clear();
    }

    @Override
    public void onReload() {
        if (config != null) {
            config.loadArenas();
        }
        if (leaderboardManager != null) {
            leaderboardManager.invalidateAll();
        }
    }

    @Override
    public List<SubCommand> getSubCommands() {
        return subCommands;
    }

    public DacArenaManager getArenaManager() {
        return arenaManager;
    }

    public DacPoolManager getPoolManager() {
        return poolManager;
    }

    public DacGameManager getGameManager() {
        return gameManager;
    }

    public DacLeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    private void registerListener(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        registeredListeners.add(listener);
    }
}
