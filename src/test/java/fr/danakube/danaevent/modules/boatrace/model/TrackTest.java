package fr.danakube.danaevent.modules.boatrace.model;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("test_world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Should initialize track with default values")
    void shouldInitializeWithDefaultValues() {
        Track track = new Track("ice_speedway", "Ice Speedway", TrackType.CIRCUIT_LAPS, TrackMode.TIME_ATTACK_247);

        assertThat(track.getId()).isEqualTo("ice_speedway");
        assertThat(track.getName()).isEqualTo("Ice Speedway");
        assertThat(track.getType()).isEqualTo(TrackType.CIRCUIT_LAPS);
        assertThat(track.getMode()).isEqualTo(TrackMode.TIME_ATTACK_247);
        assertThat(track.isCollisionsEnabled()).isFalse();
        assertThat(track.getBoatMaterial()).isEqualTo(Material.OAK_BOAT);
        assertThat(track.getLaps()).isEqualTo(1);
        assertThat(track.getSpawnPoints()).isEmpty();
        assertThat(track.getLobbyLocation()).isNull();
        assertThat(track.getStartRegion()).isNull();
        assertThat(track.getFinishRegion()).isNull();
        assertThat(track.isReady()).isFalse();
    }

    @Test
    @DisplayName("Should update track properties via setters")
    void shouldUpdateProperties() {
        Track track = new Track("sprint_valley", "Sprint Valley", TrackType.SPRINT, TrackMode.EVENT_COMPETITION);

        track.setName("Sprint Valley Updated");
        track.setType(TrackType.CIRCUIT_LAPS);
        track.setMode(TrackMode.TIME_ATTACK_247);
        track.setCollisionsEnabled(true);
        track.setBoatMaterial(Material.BIRCH_BOAT);
        track.setLaps(3);

        Location lobby = new Location(world, 10, 64, 10);
        track.setLobbyLocation(lobby);

        CuboidRegion start = new CuboidRegion(new Location(world, 0, 64, 0), new Location(world, 5, 66, 2));
        CuboidRegion finish = new CuboidRegion(new Location(world, 100, 64, 0), new Location(world, 105, 66, 2));
        track.setStartRegion(start);
        track.setFinishRegion(finish);

        assertThat(track.getName()).isEqualTo("Sprint Valley Updated");
        assertThat(track.getType()).isEqualTo(TrackType.CIRCUIT_LAPS);
        assertThat(track.getMode()).isEqualTo(TrackMode.TIME_ATTACK_247);
        assertThat(track.isCollisionsEnabled()).isTrue();
        assertThat(track.getBoatMaterial()).isEqualTo(Material.BIRCH_BOAT);
        assertThat(track.getLaps()).isEqualTo(3);
        assertThat(track.getLobbyLocation()).isEqualTo(lobby);
        assertThat(track.getStartRegion()).isEqualTo(start);
        assertThat(track.getFinishRegion()).isEqualTo(finish);
    }

    @Test
    @DisplayName("Should manage spawn points correctly")
    void shouldManageSpawnPoints() {
        Track track = new Track("track1", "Track 1", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
        Location spawn1 = new Location(world, 1, 64, 1);
        Location spawn2 = new Location(world, 2, 64, 2);

        track.addSpawnPoint(spawn1);
        track.addSpawnPoint(spawn2);

        assertThat(track.getSpawnPoints()).containsExactly(spawn1, spawn2);

        track.removeSpawnPoint(spawn1);
        assertThat(track.getSpawnPoints()).containsExactly(spawn2);

        track.setSpawnPoints(List.of(spawn1));
        assertThat(track.getSpawnPoints()).containsExactly(spawn1);

        track.clearSpawnPoints();
        assertThat(track.getSpawnPoints()).isEmpty();
    }

    @Test
    @DisplayName("isReady() should validate startRegion, finishRegion, and at least 1 spawnPoint")
    void shouldValidateIsReady() {
        Track track = new Track("track1", "Track 1", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
        assertThat(track.isReady()).isFalse();

        CuboidRegion start = new CuboidRegion(new Location(world, 0, 64, 0), new Location(world, 2, 64, 2));
        CuboidRegion finish = new CuboidRegion(new Location(world, 10, 64, 0), new Location(world, 12, 64, 2));
        Location spawn = new Location(world, 0, 64, 0);

        // Only start
        track.setStartRegion(start);
        assertThat(track.isReady()).isFalse();

        // Start + finish
        track.setFinishRegion(finish);
        assertThat(track.isReady()).isFalse();

        // Start + finish + spawnPoint -> ready!
        track.addSpawnPoint(spawn);
        assertThat(track.isReady()).isTrue();

        // Remove finish -> not ready
        track.setFinishRegion(null);
        assertThat(track.isReady()).isFalse();

        // Restore finish, clear spawns -> not ready
        track.setFinishRegion(finish);
        track.clearSpawnPoints();
        assertThat(track.isReady()).isFalse();
    }

    @Test
    @DisplayName("Should validate constructor arguments and laps")
    void shouldValidateConstructorAndLaps() {
        assertThatThrownBy(() -> new Track(null, "Name", TrackType.SPRINT, TrackMode.TIME_ATTACK_247))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Track("id", null, TrackType.SPRINT, TrackMode.TIME_ATTACK_247))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Track("id", "Name", null, TrackMode.TIME_ATTACK_247))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Track("id", "Name", TrackType.SPRINT, null))
            .isInstanceOf(NullPointerException.class);

        Track track = new Track("id", "Name", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
        assertThatThrownBy(() -> track.setLaps(0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
