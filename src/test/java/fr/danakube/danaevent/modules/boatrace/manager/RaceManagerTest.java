package fr.danakube.danaevent.modules.boatrace.manager;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.boatrace.database.BoatRaceDatabase;
import fr.danakube.danaevent.modules.boatrace.model.RaceSession;
import fr.danakube.danaevent.modules.boatrace.model.RaceState;
import fr.danakube.danaevent.modules.boatrace.model.Track;
import fr.danakube.danaevent.modules.boatrace.model.TrackMode;
import fr.danakube.danaevent.modules.boatrace.model.TrackType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RaceManagerTest {

    private ServerMock server;
    private WorldMock world;
    private DanaEventPlugin plugin;
    private BoatRaceDatabase database;
    private CollisionManager collisionManager;
    private RaceManager raceManager;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        world = server.addSimpleWorld("race_world");

        database = new BoatRaceDatabase(plugin.getDatabaseManager(), Runnable::run);
        database.initTables();

        collisionManager = new CollisionManager(server.getScoreboardManager().getMainScoreboard());
        raceManager = new RaceManager(plugin, database, collisionManager, 2000L);
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

    private Track createTestTrack(String id, TrackType type, int laps) {
        Track track = new Track(id, "Test Track", type, TrackMode.TIME_ATTACK_247);
        track.setLaps(laps);
        track.setCollisionsEnabled(false);

        Location start1 = new Location(world, 0, 60, 0);
        Location start2 = new Location(world, 5, 65, 5);
        track.setStartRegion(new CuboidRegion(start1, start2));

        Location finish1 = new Location(world, 50, 60, 0);
        Location finish2 = new Location(world, 55, 65, 5);
        track.setFinishRegion(new CuboidRegion(finish1, finish2));

        Location spawn = new Location(world, 2.5, 61.0, 2.5);
        track.addSpawnPoint(spawn);

        return track;
    }

    @Test
    @DisplayName("Should successfully start race, clear inventory and mount player in boat")
    void shouldStartRaceSuccessfully() {
        PlayerMock player = server.addPlayer("Racer");
        ItemStack diamondSword = new ItemStack(Material.DIAMOND_SWORD);
        player.getInventory().addItem(diamondSword);

        Track track = createTestTrack("sprint_track", TrackType.SPRINT, 1);

        boolean started = raceManager.startRace(player, track);
        assertThat(started).isTrue();

        assertThat(player.getVehicle()).isInstanceOf(Boat.class);
        assertThat(player.getInventory().isEmpty()).isTrue();
        assertThat(collisionManager.hasPlayer(player)).isTrue();
        assertThat(raceManager.isRacing(player.getUniqueId())).isTrue();

        RaceSession session = raceManager.getSession(player.getUniqueId()).orElseThrow();
        assertThat(session.getState()).isEqualTo(RaceState.RACING);
        assertThat(session.getTrack()).isEqualTo(track);
    }

    @Test
    @DisplayName("Should reject race start if track is not ready or player already in race")
    void shouldRejectInvalidRaceStart() {
        PlayerMock player = server.addPlayer("Racer");
        Track notReadyTrack = new Track("not_ready", "Not Ready", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);

        assertThat(raceManager.startRace(player, notReadyTrack)).isFalse();

        Track track = createTestTrack("valid_track", TrackType.SPRINT, 1);
        assertThat(raceManager.startRace(player, track)).isTrue();

        // Second start attempt for same player must fail
        assertThat(raceManager.startRace(player, track)).isFalse();
    }

    @Test
    @DisplayName("Should finish sprint race on entering finish region and restore player")
    void shouldFinishSprintRace() {
        PlayerMock player = server.addPlayer("SprintRacer");
        ItemStack bow = new ItemStack(Material.BOW);
        player.getInventory().addItem(bow);

        Track track = createTestTrack("sprint_track", TrackType.SPRINT, 1);
        raceManager.startRace(player, track);

        Boat boat = (Boat) player.getVehicle();
        assertThat(boat).isNotNull();

        Location outside = new Location(world, 20, 61, 2);
        Location insideFinish = new Location(world, 52, 61, 2);

        raceManager.handleMove(player, outside, insideFinish);

        assertThat(raceManager.isRacing(player.getUniqueId())).isFalse();
        assertThat(raceManager.getSession(player.getUniqueId())).isEmpty();

        // Player restored
        assertThat(player.getInventory().contains(Material.BOW)).isTrue();
        assertThat(collisionManager.hasPlayer(player)).isFalse();
        assertThat(boat.isValid()).isFalse();
    }

    @Test
    @DisplayName("Should progress laps and finish circuit race when all laps completed")
    void shouldProgressLapsAndFinishCircuit() {
        PlayerMock player = server.addPlayer("CircuitRacer");
        Track track = createTestTrack("circuit_track", TrackType.CIRCUIT_LAPS, 2);
        raceManager.startRace(player, track);

        RaceSession session = raceManager.getSession(player.getUniqueId()).orElseThrow();
        assertThat(session.getCurrentLap()).isEqualTo(1);

        Location outside = new Location(world, 40, 61, 2);
        Location insideFinish = new Location(world, 52, 61, 2);

        // First crossing (Lap 1 completed)
        raceManager.handleMove(player, outside, insideFinish);
        assertThat(session.getCurrentLap()).isEqualTo(2);
        assertThat(session.getLapTimes()).hasSize(1);
        assertThat(raceManager.isRacing(player.getUniqueId())).isTrue();

        // Immediate crossing -> debounced
        raceManager.handleMove(player, outside, insideFinish);
        assertThat(session.getCurrentLap()).isEqualTo(2);

        // Advance debounce timestamp
        session.setLastLapCrossingMillis(System.currentTimeMillis() - 2500);

        // Second crossing (Lap 2 completed -> finish)
        raceManager.handleMove(player, outside, insideFinish);
        assertThat(raceManager.isRacing(player.getUniqueId())).isFalse();
        assertThat(raceManager.getSession(player.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("Should enforce strict Anti-Cut when player dismounts during race")
    void shouldTriggerAntiCutOnDismount() {
        PlayerMock player = server.addPlayer("AntiCutRacer");
        ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);
        player.getInventory().addItem(totem);

        Track track = createTestTrack("anti_cut_track", TrackType.SPRINT, 1);
        raceManager.startRace(player, track);

        Boat boat = (Boat) player.getVehicle();
        assertThat(boat).isNotNull();

        // Dismount
        raceManager.handleDismount(player, boat);

        assertThat(raceManager.isRacing(player.getUniqueId())).isFalse();
        assertThat(boat.isValid()).isFalse();
        assertThat(player.getInventory().contains(Material.TOTEM_OF_UNDYING)).isTrue();
        assertThat(collisionManager.hasPlayer(player)).isFalse();
    }

    @Test
    @DisplayName("Should cleanly cancel race and clean up all active sessions")
    void shouldCleanUpSessions() {
        PlayerMock p1 = server.addPlayer("Player1");
        PlayerMock p2 = server.addPlayer("Player2");

        Track track = createTestTrack("multi_track", TrackType.SPRINT, 1);
        raceManager.startRace(p1, track);
        raceManager.startRace(p2, track);

        assertThat(raceManager.getActiveSessions()).hasSize(2);

        raceManager.cleanUp();

        assertThat(raceManager.getActiveSessions()).isEmpty();
        assertThat(raceManager.isRacing(p1.getUniqueId())).isFalse();
        assertThat(raceManager.isRacing(p2.getUniqueId())).isFalse();
        assertThat(server.getScoreboardManager().getMainScoreboard().getTeam(CollisionManager.TEAM_NAME)).isNull();
    }
}
