package fr.danakube.danaevent.modules.boatrace.manager;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.boatrace.model.Track;
import fr.danakube.danaevent.modules.boatrace.model.TrackMode;
import fr.danakube.danaevent.modules.boatrace.model.TrackType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackManagerTest {

    private ServerMock server;
    private WorldMock world;

    @TempDir
    Path tempDir;

    private File tracksFile;
    private TrackManager trackManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("racing_world");
        tracksFile = tempDir.resolve("tracks.yml").toFile();
        trackManager = new TrackManager(tracksFile);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Should create, retrieve and delete tracks in memory")
    void shouldCreateAndManageTracksInMemory() {
        Track created = trackManager.createTrack("ice_circuit", "Ice Circuit", TrackType.CIRCUIT_LAPS, TrackMode.TIME_ATTACK_247);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo("ice_circuit");
        assertThat(trackManager.getTracks()).hasSize(1);

        Optional<Track> found = trackManager.getTrack("ice_circuit");
        assertThat(found).isPresent();
        assertThat(found.get()).isSameAs(created);

        // Duplicate creation should throw
        assertThatThrownBy(() -> trackManager.createTrack("ice_circuit", "Duplicate", TrackType.SPRINT, TrackMode.EVENT_COMPETITION))
            .isInstanceOf(IllegalArgumentException.class);

        // Delete track
        boolean deleted = trackManager.deleteTrack("ice_circuit");
        assertThat(deleted).isTrue();
        assertThat(trackManager.getTrack("ice_circuit")).isEmpty();
        assertThat(trackManager.getTracks()).isEmpty();
    }

    @Test
    @DisplayName("Should serialize tracks to YAML and reload them with full state fidelity")
    void shouldSerializeAndReloadTracks() {
        Track track = trackManager.createTrack("grand_prix", "Grand Prix", TrackType.CIRCUIT_LAPS, TrackMode.EVENT_COMPETITION);
        track.setCollisionsEnabled(true);
        track.setBoatMaterial(Material.MANGROVE_BOAT);
        track.setLaps(5);

        Location lobby = new Location(world, 100.5, 70.0, 200.5, 90.0f, 0.0f);
        track.setLobbyLocation(lobby);

        CuboidRegion start = new CuboidRegion(new Location(world, 10, 60, 10), new Location(world, 15, 65, 12));
        CuboidRegion finish = new CuboidRegion(new Location(world, 50, 60, 10), new Location(world, 55, 65, 12));
        track.setStartRegion(start);
        track.setFinishRegion(finish);

        Location spawn1 = new Location(world, 11.5, 61.0, 11.0, 180.0f, 0.0f);
        Location spawn2 = new Location(world, 13.5, 61.0, 11.0, 180.0f, 0.0f);
        track.addSpawnPoint(spawn1);
        track.addSpawnPoint(spawn2);

        assertThat(track.isReady()).isTrue();

        // Save to YAML
        trackManager.saveTracks();
        assertThat(tracksFile).exists();

        // Load into a new TrackManager instance from the saved file
        TrackManager newManager = new TrackManager(tracksFile);
        newManager.loadTracks();

        assertThat(newManager.getTracks()).hasSize(1);
        Optional<Track> loadedOpt = newManager.getTrack("grand_prix");
        assertThat(loadedOpt).isPresent();

        Track loaded = loadedOpt.get();
        assertThat(loaded.getId()).isEqualTo("grand_prix");
        assertThat(loaded.getName()).isEqualTo("Grand Prix");
        assertThat(loaded.getType()).isEqualTo(TrackType.CIRCUIT_LAPS);
        assertThat(loaded.getMode()).isEqualTo(TrackMode.EVENT_COMPETITION);
        assertThat(loaded.isCollisionsEnabled()).isTrue();
        assertThat(loaded.getBoatMaterial()).isEqualTo(Material.MANGROVE_BOAT);
        assertThat(loaded.getLaps()).isEqualTo(5);

        // Check Lobby Location
        assertThat(loaded.getLobbyLocation()).isNotNull();
        assertThat(loaded.getLobbyLocation().getWorld().getName()).isEqualTo(world.getName());
        assertThat(loaded.getLobbyLocation().getX()).isEqualTo(100.5);
        assertThat(loaded.getLobbyLocation().getY()).isEqualTo(70.0);
        assertThat(loaded.getLobbyLocation().getZ()).isEqualTo(200.5);

        // Check Start Region
        assertThat(loaded.getStartRegion()).isNotNull();
        assertThat(loaded.getStartRegion().getWorldName()).isEqualTo(world.getName());
        assertThat(loaded.getStartRegion().getMinX()).isEqualTo(10);
        assertThat(loaded.getStartRegion().getMaxX()).isEqualTo(15);
        assertThat(loaded.getStartRegion().getMinY()).isEqualTo(60);
        assertThat(loaded.getStartRegion().getMaxY()).isEqualTo(65);

        // Check Finish Region
        assertThat(loaded.getFinishRegion()).isNotNull();
        assertThat(loaded.getFinishRegion().getWorldName()).isEqualTo(world.getName());
        assertThat(loaded.getFinishRegion().getMinX()).isEqualTo(50);
        assertThat(loaded.getFinishRegion().getMaxX()).isEqualTo(55);

        // Check Spawn Points
        assertThat(loaded.getSpawnPoints()).hasSize(2);
        Location loadedSpawn1 = loaded.getSpawnPoints().get(0);
        assertThat(loadedSpawn1.getWorld().getName()).isEqualTo(world.getName());
        assertThat(loadedSpawn1.getX()).isEqualTo(11.5);
        assertThat(loadedSpawn1.getY()).isEqualTo(61.0);
        assertThat(loadedSpawn1.getZ()).isEqualTo(11.0);

        // Check readiness
        assertThat(loaded.isReady()).isTrue();
    }

    @Test
    @DisplayName("loadTracks() should handle missing or empty file gracefully")
    void shouldHandleMissingFileGracefully() {
        File nonExistent = tempDir.resolve("non_existent.yml").toFile();
        TrackManager emptyManager = new TrackManager(nonExistent);

        // Should not throw
        emptyManager.loadTracks();
        assertThat(emptyManager.getTracks()).isEmpty();
    }
}
