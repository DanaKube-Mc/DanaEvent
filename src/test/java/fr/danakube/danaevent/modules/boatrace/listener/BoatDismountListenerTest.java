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
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoatDismountListenerTest {

    private ServerMock server;
    private WorldMock world;
    private DanaEventPlugin plugin;
    private RaceManager raceManager;
    private BoatDismountListener dismountListener;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        world = server.addSimpleWorld("dismount_world");

        BoatRaceDatabase database = new BoatRaceDatabase(plugin.getDatabaseManager(), Runnable::run);
        database.initTables();

        CollisionManager collisionManager = new CollisionManager(server.getScoreboardManager().getMainScoreboard());
        raceManager = new RaceManager(plugin, database, collisionManager);

        dismountListener = new BoatDismountListener(raceManager);
        server.getPluginManager().registerEvents(dismountListener, plugin);
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
    @DisplayName("Should cancel race, destroy boat and restore inventory on VehicleExitEvent (Anti-Cut)")
    void shouldTriggerAntiCutOnVehicleExitEvent() {
        PlayerMock player = server.addPlayer("Dismounter");
        player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 5));

        Track track = new Track("dismount_track", "Track", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
        track.setStartRegion(new CuboidRegion(new Location(world, 0, 60, 0), new Location(world, 2, 62, 2)));
        track.setFinishRegion(new CuboidRegion(new Location(world, 20, 60, 0), new Location(world, 22, 62, 2)));
        track.addSpawnPoint(new Location(world, 1, 61, 1));

        raceManager.startRace(player, track);
        assertThat(raceManager.isRacing(player.getUniqueId())).isTrue();

        Boat boat = (Boat) player.getVehicle();
        assertThat(boat).isNotNull();

        // Fire VehicleExitEvent
        VehicleExitEvent event = new VehicleExitEvent(boat, player);
        server.getPluginManager().callEvent(event);

        assertThat(raceManager.isRacing(player.getUniqueId())).isFalse();
        assertThat(boat.isValid()).isFalse();
        assertThat(player.getInventory().contains(Material.GOLDEN_APPLE)).isTrue();
    }

    @Test
    @DisplayName("Should ignore VehicleExitEvent if player is not in an active race")
    void shouldIgnoreExitWhenNotRacing() {
        PlayerMock player = server.addPlayer("NormalDismounter");
        Boat boat = world.spawn(new Location(world, 10, 64, 10), Boat.class);
        boat.addPassenger(player);

        VehicleExitEvent event = new VehicleExitEvent(boat, player);
        server.getPluginManager().callEvent(event);

        // Boat remains valid as it is not part of a race
        assertThat(boat.isValid()).isTrue();
    }
}
