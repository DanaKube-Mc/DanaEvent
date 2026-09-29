package fr.danakube.danaevent.modules.boatrace.manager;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.boatrace.database.BoatRaceDatabase;
import fr.danakube.danaevent.modules.boatrace.model.RaceSession;
import fr.danakube.danaevent.modules.boatrace.model.RaceState;
import fr.danakube.danaevent.modules.boatrace.model.Track;
import fr.danakube.danaevent.modules.boatrace.model.TrackType;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active boat races, lifecycle states, line crossings, anti-cut mechanics,
 * and player state restoration.
 */
public class RaceManager {

    private static final long DEFAULT_DEBOUNCE_MILLIS = 2000L;

    private final DanaEventPlugin plugin;
    private final BoatRaceDatabase database;
    private final CollisionManager collisionManager;
    private final Map<UUID, RaceSession> activeSessions = new ConcurrentHashMap<>();
    private final long debounceMillis;

    public RaceManager(DanaEventPlugin plugin, BoatRaceDatabase database, CollisionManager collisionManager) {
        this(plugin, database, collisionManager, DEFAULT_DEBOUNCE_MILLIS);
    }

    public RaceManager(DanaEventPlugin plugin, BoatRaceDatabase database, CollisionManager collisionManager, long debounceMillis) {
        this.plugin = Objects.requireNonNull(plugin, "DanaEventPlugin cannot be null");
        this.database = Objects.requireNonNull(database, "BoatRaceDatabase cannot be null");
        this.collisionManager = Objects.requireNonNull(collisionManager, "CollisionManager cannot be null");
        this.debounceMillis = debounceMillis;
    }

    /**
     * Starts a race session for the given player on the specified track.
     *
     * @param player the player participating
     * @param track the track to race on
     * @return true if started successfully, false otherwise
     */
    public boolean startRace(Player player, Track track) {
        if (player == null || track == null || !track.isReady()) {
            return false;
        }

        UUID playerUuid = player.getUniqueId();
        if (activeSessions.containsKey(playerUuid)) {
            return false;
        }

        // 1. Save and clear player inventory/state
        boolean saved = plugin.getPlayerStateManager().saveAndClear(player);
        if (!saved) {
            return false;
        }

        // 2. Pick spawn point and teleport player
        Location spawnLoc = track.getSpawnPoints().getFirst().clone();
        player.teleport(spawnLoc);

        // 3. Spawn boat and mount player
        Boat boat = spawnLoc.getWorld().spawn(spawnLoc, Boat.class);
        boat.addPassenger(player);

        // 4. Configure collisions
        if (!track.isCollisionsEnabled()) {
            collisionManager.addPlayer(player);
        }

        // 5. Start race session
        long startTime = System.currentTimeMillis();
        RaceSession session = new RaceSession(playerUuid, track, boat, startTime, RaceState.RACING);
        activeSessions.put(playerUuid, session);

        // 6. Notify player
        plugin.getMessageManager().sendMessage(player, "boatrace-start");

        return true;
    }

    /**
     * Handles movement detection for a racing player.
     * Detects crossing of the finish line region, updates laps, or triggers race finish.
     *
     * @param player the moving player
     * @param from origin location
     * @param to destination location
     */
    public void handleMove(Player player, Location from, Location to) {
        if (player == null || to == null) {
            return;
        }

        RaceSession session = activeSessions.get(player.getUniqueId());
        if (session == null || session.getState() != RaceState.RACING) {
            return;
        }

        Track track = session.getTrack();
        CuboidRegion finishRegion = track.getFinishRegion();
        if (finishRegion == null || !finishRegion.contains(to)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!session.canCrossLine(debounceMillis, now)) {
            return;
        }

        if (track.getType() == TrackType.SPRINT) {
            session.setLastLapCrossingMillis(now);
            finishRace(player, session);
        } else {
            long lastCrossing = session.getLastLapCrossingMillis() > 0 ? session.getLastLapCrossingMillis() : session.getStartTimeMillis();
            long lapDuration = Math.max(0, now - lastCrossing);
            session.recordLap(lapDuration);
            session.setLastLapCrossingMillis(now);

            int currentLap = session.getCurrentLap();
            if (currentLap >= track.getLaps()) {
                finishRace(player, session);
            } else {
                int completedLap = currentLap;
                session.setCurrentLap(completedLap + 1);
                plugin.getMessageManager().sendMessage(
                    player,
                    "boatrace-lap",
                    Placeholder.parsed("lap", String.valueOf(completedLap)),
                    Placeholder.parsed("total_laps", String.valueOf(track.getLaps())),
                    Placeholder.parsed("time", RaceSession.formatTime(lapDuration))
                );
            }
        }
    }

    /**
     * Completes the race session, records database stats, restores player state, and removes boat.
     *
     * @param player the player finishing the race
     * @param session the active race session
     */
    public void finishRace(Player player, RaceSession session) {
        if (session == null) {
            return;
        }

        session.setState(RaceState.FINISHED);
        activeSessions.remove(session.getPlayerUuid());

        long totalTime = session.getElapsedTimeMillis();
        String formatted = RaceSession.formatTime(totalTime);

        if (player != null && player.isOnline()) {
            plugin.getMessageManager().sendMessage(
                player,
                "boatrace-finish",
                Placeholder.parsed("time", formatted)
            );
        }

        // Persist record to database asynchronously and update caches if available
        if (plugin != null && plugin.getBoatRaceLeaderboardManager() != null) {
            plugin.getBoatRaceLeaderboardManager().recordTime(session.getTrack().getId(), session.getPlayerUuid(), totalTime, session.getCurrentLap());
        } else {
            database.saveRecord(session.getTrack().getId(), session.getPlayerUuid(), totalTime, session.getCurrentLap());
        }

        // Destroy boat
        removeBoat(session);

        // Remove from collision team
        if (player != null) {
            collisionManager.removePlayer(player);
            // Restore player state and location
            plugin.getPlayerStateManager().restore(player, true);
        }
    }

    /**
     * Strict Anti-Cut mechanic: triggers if the player dismounts/sneaks out of the boat during a race.
     *
     * @param player the player who exited
     * @param vehicle the vehicle exited
     */
    public void handleDismount(Player player, Vehicle vehicle) {
        if (player == null) {
            return;
        }

        RaceSession session = activeSessions.remove(player.getUniqueId());
        if (session == null || session.getState() != RaceState.RACING) {
            return;
        }

        session.setState(RaceState.CANCELLED);

        // Remove vehicle immediately
        if (vehicle != null && vehicle.isValid()) {
            vehicle.remove();
        }
        removeBoat(session);

        // Remove from collision manager
        collisionManager.removePlayer(player);

        // Send anti-cut alert
        plugin.getMessageManager().sendMessage(player, "boatrace-anti-cut");

        // Restore player inventory and state
        plugin.getPlayerStateManager().restore(player, true);
    }

    /**
     * Cancels an ongoing race session with a reason message.
     *
     * @param player the player whose race is cancelled
     * @param reason cancellation reason
     */
    public void cancelRace(Player player, String reason) {
        if (player == null) {
            return;
        }

        RaceSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }

        session.setState(RaceState.CANCELLED);
        removeBoat(session);
        collisionManager.removePlayer(player);
        plugin.getPlayerStateManager().restore(player, true);

        if (player.isOnline()) {
            plugin.getMessageManager().sendMessage(
                player,
                "boatrace-cancelled",
                Placeholder.parsed("reason", reason != null ? reason : "Course interrompue")
            );
        }
    }

    /**
     * Cleans up all active sessions, destroys all spawned boats, and unregisters collision teams.
     */
    public void cleanUp() {
        for (RaceSession session : activeSessions.values()) {
            session.setState(RaceState.CANCELLED);
            removeBoat(session);
            Player player = Bukkit.getPlayer(session.getPlayerUuid());
            if (player != null && player.isOnline()) {
                collisionManager.removePlayer(player);
                plugin.getPlayerStateManager().restore(player, true);
            }
        }
        activeSessions.clear();
        collisionManager.cleanUp();
    }

    /**
     * Checks if a player is currently in an active RACING session.
     *
     * @param playerUuid player's UUID
     * @return true if currently racing
     */
    public boolean isRacing(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        RaceSession session = activeSessions.get(playerUuid);
        return session != null && session.getState() == RaceState.RACING;
    }

    /**
     * Checks if a vehicle belongs to an active racing session.
     *
     * @param entity the entity to check
     * @return true if vehicle is an active race boat
     */
    public boolean isBoatInRace(Entity entity) {
        if (entity == null) {
            return false;
        }
        UUID entityUuid = entity.getUniqueId();
        for (RaceSession session : activeSessions.values()) {
            if (session.getState() == RaceState.RACING && entityUuid.equals(session.getBoatUuid())) {
                return true;
            }
        }
        return false;
    }

    public Optional<RaceSession> getSession(UUID playerUuid) {
        if (playerUuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeSessions.get(playerUuid));
    }

    public Map<UUID, RaceSession> getActiveSessions() {
        return Collections.unmodifiableMap(activeSessions);
    }

    private void removeBoat(RaceSession session) {
        Boat boat = session.getBoat();
        if (boat != null && boat.isValid()) {
            boat.remove();
        }
    }
}
