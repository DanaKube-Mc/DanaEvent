package fr.danakube.danaevent.modules.boatrace;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.module.AbstractDanaModule;
import fr.danakube.danaevent.modules.boatrace.command.BoatRaceAdminCmd;
import fr.danakube.danaevent.modules.boatrace.command.BoatRaceJoinCmd;
import fr.danakube.danaevent.modules.boatrace.command.BoatRaceLeaveCmd;
import fr.danakube.danaevent.modules.boatrace.command.BoatRaceListCmd;
import fr.danakube.danaevent.modules.boatrace.command.BoatRaceTopCmd;
import fr.danakube.danaevent.modules.boatrace.database.BoatRaceDatabase;
import fr.danakube.danaevent.modules.boatrace.listener.BoatDismountListener;
import fr.danakube.danaevent.modules.boatrace.listener.BoatMoveListener;
import fr.danakube.danaevent.modules.boatrace.listener.BoatProtectionListener;
import fr.danakube.danaevent.modules.boatrace.manager.BoatRaceLeaderboardManager;
import fr.danakube.danaevent.modules.boatrace.manager.CollisionManager;
import fr.danakube.danaevent.modules.boatrace.manager.RaceManager;
import fr.danakube.danaevent.modules.boatrace.manager.TrackManager;
import fr.danakube.danaevent.modules.boatrace.model.Track;
import fr.danakube.danaevent.modules.boatrace.task.RaceHudTask;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Pluggable DanaEvent module for ice boat races.
 */
public class BoatRaceModule extends AbstractDanaModule {

    private final DanaEventPlugin plugin;
    private BoatRaceDatabase database;
    private TrackManager trackManager;
    private CollisionManager collisionManager;
    private RaceManager raceManager;
    private BoatRaceLeaderboardManager leaderboardManager;
    private RaceHudTask hudTask;
    private BoatMoveListener moveListener;
    private BoatDismountListener dismountListener;
    private BoatProtectionListener protectionListener;
    private fr.danakube.danaevent.modules.boatrace.manager.BoatSkinManager boatSkinManager;
    private fr.danakube.danaevent.modules.boatrace.listener.BoatRacePlayerListener playerListener;

    public BoatRaceModule(DanaEventPlugin plugin) {
        super("boatrace", List.of("br"), "Course de Bateaux", "1.0.0");
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    public void onEnable() {
        super.onEnable();

        this.database = new BoatRaceDatabase(plugin.getDatabaseManager());
        try {
            database.initTables();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize boatrace tables: " + e.getMessage(), e);
        }

        this.trackManager = new TrackManager(plugin);
        this.trackManager.loadTracks();

        this.boatSkinManager = new fr.danakube.danaevent.modules.boatrace.manager.BoatSkinManager(plugin, database);
        this.boatSkinManager.loadBoatsConfig();

        this.collisionManager = new CollisionManager();
        this.raceManager = new RaceManager(plugin, database, collisionManager);
        this.leaderboardManager = new BoatRaceLeaderboardManager(plugin, database);
        BoatRaceLeaderboardManager.setInstance(this.leaderboardManager);
        plugin.setBoatRaceLeaderboardManager(this.leaderboardManager);

        this.moveListener = new BoatMoveListener(raceManager);
        this.dismountListener = new BoatDismountListener(raceManager);
        this.protectionListener = new BoatProtectionListener(raceManager);
        this.playerListener = new fr.danakube.danaevent.modules.boatrace.listener.BoatRacePlayerListener(boatSkinManager);

        Bukkit.getPluginManager().registerEvents(moveListener, plugin);
        Bukkit.getPluginManager().registerEvents(dismountListener, plugin);
        Bukkit.getPluginManager().registerEvents(protectionListener, plugin);
        Bukkit.getPluginManager().registerEvents(playerListener, plugin);

        this.hudTask = new RaceHudTask(raceManager);
        this.hudTask.runTaskTimer(plugin, 0L, 2L);

        if (getSubCommands().isEmpty()) {
            registerSubCommand(new BoatRaceListCmd(plugin, this));
            registerSubCommand(new BoatRaceJoinCmd(plugin, this));
            registerSubCommand(new BoatRaceLeaveCmd(plugin, this));
            registerSubCommand(new BoatRaceTopCmd(plugin, this));
            registerSubCommand(new fr.danakube.danaevent.modules.boatrace.command.BoatRaceBoatCmd(plugin, this));
            registerSubCommand(new fr.danakube.danaevent.modules.boatrace.command.BoatRaceHudCmd(plugin, this));
            registerSubCommand(new BoatRaceAdminCmd(plugin, this));
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();

        if (hudTask != null) {
            hudTask.cancel();
            hudTask = null;
        }

        if (raceManager != null) {
            raceManager.cleanUp();
        }

        if (collisionManager != null) {
            collisionManager.cleanUp();
        }

        if (boatSkinManager != null) {
            boatSkinManager.cleanUp();
            boatSkinManager = null;
        }

        if (leaderboardManager != null) {
            leaderboardManager.invalidateAll();
            if (plugin.getBoatRaceLeaderboardManager() == this.leaderboardManager) {
                plugin.setBoatRaceLeaderboardManager(null);
            }
        }
        BoatRaceLeaderboardManager.setInstance(null);

        if (moveListener != null) {
            HandlerList.unregisterAll(moveListener);
            moveListener = null;
        }
        if (dismountListener != null) {
            HandlerList.unregisterAll(dismountListener);
            dismountListener = null;
        }
        if (protectionListener != null) {
            HandlerList.unregisterAll(protectionListener);
            protectionListener = null;
        }
        if (playerListener != null) {
            HandlerList.unregisterAll(playerListener);
            playerListener = null;
        }
    }

    @Override
    public void onReload() {
        if (trackManager != null) {
            trackManager.loadTracks();
            if (leaderboardManager != null) {
                for (Track track : trackManager.getTracks()) {
                    leaderboardManager.refreshCache(track.getId());
                }
            }
        }
        if (boatSkinManager != null) {
            boatSkinManager.loadBoatsConfig();
        }
    }

    public DanaEventPlugin getPlugin() {
        return plugin;
    }

    public BoatRaceDatabase getDatabase() {
        return database;
    }

    public TrackManager getTrackManager() {
        return trackManager;
    }

    public CollisionManager getCollisionManager() {
        return collisionManager;
    }

    public RaceManager getRaceManager() {
        return raceManager;
    }

    public BoatRaceLeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public RaceHudTask getHudTask() {
        return hudTask;
    }

    public fr.danakube.danaevent.modules.boatrace.manager.BoatSkinManager getBoatSkinManager() {
        return boatSkinManager;
    }
}
