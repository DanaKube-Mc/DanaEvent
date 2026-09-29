package fr.danakube.danaevent.modules.boatrace.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.boatrace.database.BoatRaceDatabase;
import fr.danakube.danaevent.modules.boatrace.manager.CollisionManager;
import fr.danakube.danaevent.modules.boatrace.manager.RaceManager;
import fr.danakube.danaevent.modules.boatrace.model.Track;
import fr.danakube.danaevent.modules.boatrace.model.TrackMode;
import fr.danakube.danaevent.modules.boatrace.model.TrackType;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoatMoveListenerTest {

    private ServerMock server;
    private WorldMock world;
    private DanaEventPlugin plugin;
    private RaceManager raceManager;
    private BoatMoveListener moveListener;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        world = server.addSimpleWorld("move_world");

        BoatRaceDatabase database = new BoatRaceDatabase(plugin.getDatabaseManager(), Runnable::run);
        database.initTables();

        CollisionManager collisionManager = new CollisionManager(server.getScoreboardManager().getMainScoreboard());
        raceManager = new RaceManager(plugin, database, collisionManager);

        moveListener = new BoatMoveListener(raceManager);
        server.getPluginManager().registerEvents(moveListener, plugin);
    }

    @AfterEach
    void tearDown() {
        if (raceManager != null) {
            raceManager.cleanUp();
        }
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should detect finish line crossing on VehicleMoveEvent")
    void shouldTriggerHandleMoveOnVehicleMoveEvent() {
        PlayerMock player = server.addPlayer("MoveRacer");

        Track track = new Track("move_track", "Track", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
        track.setStartRegion(new CuboidRegion(new Location(world, 0, 60, 0), new Location(world, 2, 62, 2)));
        track.setFinishRegion(new CuboidRegion(new Location(world, 20, 60, 0), new Location(world, 22, 62, 2)));
        track.addSpawnPoint(new Location(world, 1, 61, 1));

        raceManager.startRace(player, track);
        Boat boat = (Boat) player.getVehicle();
        assertThat(boat).isNotNull();

        Location from = new Location(world, 10, 61, 1);
        Location to = new Location(world, 21, 61, 1);

        VehicleMoveEvent moveEvent = new VehicleMoveEvent(boat, from, to);
        server.getPluginManager().callEvent(moveEvent);

        assertThat(raceManager.isRacing(player.getUniqueId())).isFalse();
    }
}
