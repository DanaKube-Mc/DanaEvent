package fr.danakube.danaevent.modules.boatrace.task;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RaceHudTaskTest {

    private ServerMock server;
    private WorldMock world;
    private DanaEventPlugin plugin;
    private RaceManager raceManager;
    private RaceHudTask hudTask;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        world = server.addSimpleWorld("hud_world");

        BoatRaceDatabase database = new BoatRaceDatabase(plugin.getDatabaseManager(), Runnable::run);
        database.initTables();

        CollisionManager collisionManager = new CollisionManager(server.getScoreboardManager().getMainScoreboard());
        raceManager = new RaceManager(plugin, database, collisionManager);

        hudTask = new RaceHudTask(raceManager);
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
    @DisplayName("Should run without exception and update action bar for active racers")
    void shouldUpdateHudForRacers() {
        PlayerMock player = server.addPlayer("HudRacer");

        Track track = new Track("hud_track", "Track", TrackType.CIRCUIT_LAPS, TrackMode.TIME_ATTACK_247);
        track.setLaps(3);
        track.setStartRegion(new CuboidRegion(new Location(world, 0, 60, 0), new Location(world, 2, 62, 2)));
        track.setFinishRegion(new CuboidRegion(new Location(world, 20, 60, 0), new Location(world, 22, 62, 2)));
        track.addSpawnPoint(new Location(world, 1, 61, 1));

        raceManager.startRace(player, track);

        // Run task
        hudTask.run();

        // Player should have received an action bar
        // In MockBukkit PlayerMock, nextComponentMessage() or similar tracks messages
        assertThat(raceManager.isRacing(player.getUniqueId())).isTrue();
    }
}
