package fr.danakube.danaevent.modules.deacoudre.config;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.deacoudre.manager.DacArenaManager;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameFormat;
import fr.danakube.danaevent.modules.deacoudre.model.DacTeamLifeMode;
import fr.danakube.danaevent.modules.deacoudre.model.JumpMode;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DacConfigTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private WorldMock world;
    private File configFile;
    private DacConfig config;
    private DacArenaManager manager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("dac_config_world");
        configFile = tempDir.resolve("dac_arenas.yml").toFile();
        config = new DacConfig(configFile);
        manager = new DacArenaManager(config);
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should create, save and reload arenas from YAML file")
    void testCreateSaveAndReload() {
        DacArena arena = manager.createArena("lagoon", "Le Lagon", DacGameFormat.TEAM, JumpMode.SIMULTANEOUS_WAVE);
        arena.setTeamLifeMode(DacTeamLifeMode.SHARED_POOL);
        arena.setInitialLives(5);
        arena.setJumpTimeSeconds(20);
        arena.setPoolRegion(new CuboidRegion(world.getName(), -10, 50, -10, 10, 60, 10));
        arena.setDivingLocation(new Location(world, 0, 85, 0));
        arena.setLobbyLocation(new Location(world, 0, 60, -20));

        manager.saveArenas();
        assertThat(configFile).exists();

        DacConfig reloadedConfig = new DacConfig(configFile);
        reloadedConfig.loadArenas();

        assertThat(reloadedConfig.getArenas()).hasSize(1);
        DacArena loaded = reloadedConfig.getArena("lagoon").orElse(null);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getDisplayName()).isEqualTo("Le Lagon");
        assertThat(loaded.getFormat()).isEqualTo(DacGameFormat.TEAM);
        assertThat(loaded.getJumpMode()).isEqualTo(JumpMode.SIMULTANEOUS_WAVE);
        assertThat(loaded.getTeamLifeMode()).isEqualTo(DacTeamLifeMode.SHARED_POOL);
        assertThat(loaded.getInitialLives()).isEqualTo(5);
        assertThat(loaded.getJumpTimeSeconds()).isEqualTo(20);
        assertThat(loaded.getPoolRegion()).isNotNull();
        assertThat(loaded.getPoolRegion().getMinX()).isEqualTo(-10);
        assertThat(loaded.getDivingLocation()).isNotNull();
        assertThat(loaded.getDivingLocation().getY()).isEqualTo(85);
        assertThat(loaded.getLobbyLocation()).isNotNull();
        assertThat(loaded.isReady()).isTrue();
    }

    @Test
    @DisplayName("Should handle duplicate IDs and deletion properly")
    void testDuplicateAndDeletion() {
        manager.createArena("pool1", "Pool One", DacGameFormat.SOLO, JumpMode.TURN_BY_TURN);
        assertThatThrownBy(() -> manager.createArena("pool1", "Pool One Duplicate", DacGameFormat.SOLO, JumpMode.TURN_BY_TURN))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(manager.deleteArena("pool1")).isTrue();
        assertThat(manager.getArena("pool1")).isEmpty();
        assertThat(manager.deleteArena("non_existent")).isFalse();
    }
}
