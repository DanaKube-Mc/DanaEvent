package fr.danakube.danaevent.modules.treasurehunt;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.module.AbstractDanaModule;
import fr.danakube.danaevent.modules.treasurehunt.command.HuntAdminCmd;
import fr.danakube.danaevent.modules.treasurehunt.command.HuntJoinCmd;
import fr.danakube.danaevent.modules.treasurehunt.command.HuntJournalCmd;
import fr.danakube.danaevent.modules.treasurehunt.command.HuntLeaveCmd;
import fr.danakube.danaevent.modules.treasurehunt.command.HuntListCmd;
import fr.danakube.danaevent.modules.treasurehunt.command.HuntTopCmd;
import fr.danakube.danaevent.modules.treasurehunt.config.HuntConfig;
import fr.danakube.danaevent.modules.treasurehunt.database.TreasureHuntDatabase;
import fr.danakube.danaevent.modules.treasurehunt.display.ClueParticleTask;
import fr.danakube.danaevent.modules.treasurehunt.display.HuntHudTask;
import fr.danakube.danaevent.modules.treasurehunt.listener.HuntBlockInteractListener;
import fr.danakube.danaevent.modules.treasurehunt.listener.HuntChatAnswerListener;
import fr.danakube.danaevent.modules.treasurehunt.listener.HuntTeamSyncListener;
import fr.danakube.danaevent.modules.treasurehunt.listener.HuntZoneMoveListener;
import fr.danakube.danaevent.modules.treasurehunt.manager.HuntLeaderboardManager;
import fr.danakube.danaevent.modules.treasurehunt.manager.HuntPathDistributor;
import fr.danakube.danaevent.modules.treasurehunt.manager.HuntProgressManager;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Pluggable DanaEvent module for multi-step interactive and anti-sheep treasure hunts.
 */
public class TreasureHuntModule extends AbstractDanaModule {

    private final DanaEventPlugin plugin;
    private TreasureHuntDatabase database;
    private HuntConfig huntConfig;
    private HuntPathDistributor pathDistributor;
    private HuntProgressManager progressManager;
    private HuntLeaderboardManager leaderboardManager;

    private HuntHudTask hudTask;
    private ClueParticleTask particleTask;

    private HuntBlockInteractListener blockListener;
    private HuntZoneMoveListener zoneListener;
    private HuntChatAnswerListener chatListener;
    private HuntTeamSyncListener teamSyncListener;

    public TreasureHuntModule(DanaEventPlugin plugin) {
        super("treasurehunt", List.of("hunt", "th"), "Chasse au Trésor", "1.0.0");
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    public void onEnable() {
        super.onEnable();

        this.database = new TreasureHuntDatabase(plugin.getDatabaseManager());
        try {
            database.initTables();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize treasurehunt tables: " + e.getMessage(), e);
        }

        File configFile = new File(plugin.getDataFolder(), "modules/treasurehunt/hunts.yml");
        this.huntConfig = new HuntConfig(configFile);
        this.huntConfig.loadHunts();

        this.pathDistributor = new HuntPathDistributor();
        this.progressManager = new HuntProgressManager(plugin, huntConfig, database, pathDistributor);
        this.leaderboardManager = new HuntLeaderboardManager(plugin, database);
        HuntLeaderboardManager.setInstance(this.leaderboardManager);

        this.blockListener = new HuntBlockInteractListener(plugin, progressManager, huntConfig);
        this.zoneListener = new HuntZoneMoveListener(plugin, progressManager, huntConfig);
        this.chatListener = new HuntChatAnswerListener(plugin, progressManager, huntConfig);
        this.teamSyncListener = new HuntTeamSyncListener(plugin, progressManager, huntConfig);

        Bukkit.getPluginManager().registerEvents(blockListener, plugin);
        Bukkit.getPluginManager().registerEvents(zoneListener, plugin);
        Bukkit.getPluginManager().registerEvents(chatListener, plugin);
        Bukkit.getPluginManager().registerEvents(teamSyncListener, plugin);

        this.hudTask = new HuntHudTask(progressManager, huntConfig);
        this.hudTask.runTaskTimer(plugin, 0L, 10L);

        this.particleTask = new ClueParticleTask(progressManager, huntConfig);
        this.particleTask.runTaskTimer(plugin, 0L, 20L);

        if (getSubCommands().isEmpty()) {
            registerSubCommand(new HuntListCmd(plugin, this));
            registerSubCommand(new HuntJoinCmd(plugin, this));
            registerSubCommand(new HuntLeaveCmd(plugin, this));
            registerSubCommand(new HuntJournalCmd(plugin, this));
            registerSubCommand(new HuntTopCmd(plugin, this));
            registerSubCommand(new HuntAdminCmd(plugin, this));
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();

        if (hudTask != null) {
            hudTask.cancel();
            hudTask = null;
        }

        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }

        if (progressManager != null) {
            progressManager.cleanUp();
        }

        if (leaderboardManager != null) {
            leaderboardManager.cleanUp();
            leaderboardManager = null;
        }
        HuntLeaderboardManager.setInstance(null);

        if (blockListener != null) {
            HandlerList.unregisterAll(blockListener);
            blockListener = null;
        }
        if (zoneListener != null) {
            HandlerList.unregisterAll(zoneListener);
            zoneListener = null;
        }
        if (chatListener != null) {
            HandlerList.unregisterAll(chatListener);
            chatListener = null;
        }
        if (teamSyncListener != null) {
            HandlerList.unregisterAll(teamSyncListener);
            teamSyncListener = null;
        }
    }

    @Override
    public void onReload() {
        if (huntConfig != null) {
            huntConfig.loadHunts();
        }
        if (leaderboardManager != null) {
            leaderboardManager.invalidateAll();
        }
    }

    public DanaEventPlugin getPlugin() {
        return plugin;
    }

    public TreasureHuntDatabase getDatabase() {
        return database;
    }

    public HuntConfig getHuntConfig() {
        return huntConfig;
    }

    public HuntPathDistributor getPathDistributor() {
        return pathDistributor;
    }

    public HuntProgressManager getProgressManager() {
        return progressManager;
    }

    public HuntLeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public HuntHudTask getHudTask() {
        return hudTask;
    }

    public ClueParticleTask getParticleTask() {
        return particleTask;
    }
}
