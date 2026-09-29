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
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoatProtectionListenerTest {

    private ServerMock server;
    private WorldMock world;
    private DanaEventPlugin plugin;
    private RaceManager raceManager;
    private BoatProtectionListener protectionListener;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        world = server.addSimpleWorld("protection_world");

        BoatRaceDatabase database = new BoatRaceDatabase(plugin.getDatabaseManager(), Runnable::run);
        database.initTables();

        CollisionManager collisionManager = new CollisionManager(server.getScoreboardManager().getMainScoreboard());
        raceManager = new RaceManager(plugin, database, collisionManager);

        protectionListener = new BoatProtectionListener(raceManager);
        server.getPluginManager().registerEvents(protectionListener, plugin);
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
    @DisplayName("Should cancel VehicleDamageEvent for boats in an active race")
    void shouldCancelDamageOnRaceBoat() {
        PlayerMock player = server.addPlayer("ProtectedRacer");

        Track track = new Track("prot_track", "Track", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
        track.setStartRegion(new CuboidRegion(new Location(world, 0, 60, 0), new Location(world, 2, 62, 2)));
        track.setFinishRegion(new CuboidRegion(new Location(world, 20, 60, 0), new Location(world, 22, 62, 2)));
        track.addSpawnPoint(new Location(world, 1, 61, 1));

        raceManager.startRace(player, track);
        Boat raceBoat = (Boat) player.getVehicle();
        assertThat(raceBoat).isNotNull();

        PlayerMock attacker = server.addPlayer("Attacker");
        VehicleDamageEvent damageEvent = new VehicleDamageEvent(raceBoat, attacker, 5.0);
        server.getPluginManager().callEvent(damageEvent);

        assertThat(damageEvent.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Should cancel VehicleDestroyEvent for boats in an active race")
    void shouldCancelDestroyOnRaceBoat() {
        PlayerMock player = server.addPlayer("ProtectedRacer2");

        Track track = new Track("prot_track2", "Track", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
        track.setStartRegion(new CuboidRegion(new Location(world, 0, 60, 0), new Location(world, 2, 62, 2)));
        track.setFinishRegion(new CuboidRegion(new Location(world, 20, 60, 0), new Location(world, 22, 62, 2)));
        track.addSpawnPoint(new Location(world, 1, 61, 1));

        raceManager.startRace(player, track);
        Boat raceBoat = (Boat) player.getVehicle();
        assertThat(raceBoat).isNotNull();

        PlayerMock attacker = server.addPlayer("Attacker2");
        VehicleDestroyEvent destroyEvent = new VehicleDestroyEvent(raceBoat, attacker);
        server.getPluginManager().callEvent(destroyEvent);

        assertThat(destroyEvent.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Should not cancel damage or destroy events on normal non-racing boats")
    void shouldNotCancelEventsOnNonRaceBoat() {
        Boat normalBoat = world.spawn(new Location(world, 10, 64, 10), Boat.class);
        PlayerMock attacker = server.addPlayer("Attacker3");

        VehicleDamageEvent damageEvent = new VehicleDamageEvent(normalBoat, attacker, 2.0);
        server.getPluginManager().callEvent(damageEvent);
        assertThat(damageEvent.isCancelled()).isFalse();

        VehicleDestroyEvent destroyEvent = new VehicleDestroyEvent(normalBoat, attacker);
        server.getPluginManager().callEvent(destroyEvent);
        assertThat(destroyEvent.isCancelled()).isFalse();
    }
}
