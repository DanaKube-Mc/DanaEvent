package fr.danakube.danaevent.modules.chromaticsheep.config;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.chromaticsheep.manager.ArenaManager;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameFormat;
import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepArena;
import org.bukkit.Location;
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

class ArenaConfigTest {

    private ServerMock server;
    private WorldMock world;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("arena_cfg_world");
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("loadArenas should handle missing file gracefully")
    void shouldHandleMissingFile() {
        File file = new File(tempDir.toFile(), "missing_arenas.yml");
        ArenaConfig config = new ArenaConfig(file);
        config.loadArenas();
        assertThat(config.getArenas()).isEmpty();
    }

    @Test
    @DisplayName("Should serialize arenas with bounds and spawns to YAML and reload with full fidelity")
    void shouldSerializeAndReloadArenas() {
        File file = new File(tempDir.toFile(), "arenas.yml");
        ArenaConfig config = new ArenaConfig(file);

        SheepArena arena = config.createArena("meadow_arena", "Prairie Colorée", GameFormat.TEAM, ScoringMode.DOMINATION_TICK);
        arena.setSheepCount(60);
        arena.setDurationSeconds(150);

        CuboidRegion bounds = new CuboidRegion(
            new Location(world, 10, 60, 10),
            new Location(world, 50, 80, 50)
        );
        arena.setBounds(bounds);
        arena.addPlayerSpawn(new Location(world, 15, 61, 15));
        arena.addPlayerSpawn(new Location(world, 45, 61, 45));

        config.saveArenas();
        assertThat(file.exists()).isTrue();

        // Reload into a new ArenaConfig instance
        ArenaConfig reloaded = new ArenaConfig(file);
        reloaded.loadArenas();

        assertThat(reloaded.getArenas()).hasSize(1);
        Optional<SheepArena> loadedOpt = reloaded.getArena("meadow_arena");
        assertThat(loadedOpt).isPresent();

        SheepArena loaded = loadedOpt.get();
        assertThat(loaded.getId()).isEqualTo("meadow_arena");
        assertThat(loaded.getDisplayName()).isEqualTo("Prairie Colorée");
        assertThat(loaded.getFormat()).isEqualTo(GameFormat.TEAM);
        assertThat(loaded.getScoringMode()).isEqualTo(ScoringMode.DOMINATION_TICK);
        assertThat(loaded.getSheepCount()).isEqualTo(60);
        assertThat(loaded.getDurationSeconds()).isEqualTo(150);
        assertThat(loaded.isEnabled()).isTrue();
        assertThat(loaded.isReady()).isTrue();

        assertThat(loaded.getBounds()).isNotNull();
        assertThat(loaded.getBounds().contains(new Location(world, 25, 65, 25))).isTrue();
        assertThat(loaded.getPlayerSpawns()).hasSize(2);
        assertThat(loaded.getPlayerSpawns().get(0).getBlockX()).isEqualTo(15);
        assertThat(loaded.getPlayerSpawns().get(1).getBlockX()).isEqualTo(45);

        // Delete arena
        assertThat(reloaded.deleteArena("meadow_arena")).isTrue();
        assertThat(reloaded.getArena("meadow_arena")).isEmpty();
    }

    @Test
    @DisplayName("Should prevent duplicate arena creation")
    void shouldPreventDuplicateArenaCreation() {
        File file = new File(tempDir.toFile(), "arenas.yml");
        ArenaConfig config = new ArenaConfig(file);

        config.createArena("arena_dup", "Dup Test", GameFormat.SOLO, ScoringMode.FINAL_COUNT);
        assertThatThrownBy(() -> config.createArena("arena_dup", "Dup Test 2", GameFormat.SOLO, ScoringMode.FINAL_COUNT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("ArenaManager should delegate operations to ArenaConfig accurately")
    void shouldDelegateArenaManager() {
        File file = new File(tempDir.toFile(), "manager_arenas.yml");
        ArenaConfig config = new ArenaConfig(file);
        ArenaManager manager = new ArenaManager(config);

        SheepArena arena = manager.createArena("farm", "Ferme", GameFormat.SOLO, ScoringMode.ACTION_SCORE);
        assertThat(manager.getArena("farm")).contains(arena);
        assertThat(manager.getArenas()).contains(arena);

        manager.saveArenas();
        assertThat(file.exists()).isTrue();

        manager.loadArenas();
        assertThat(manager.getArena("farm")).isPresent();

        assertThat(manager.deleteArena("farm")).isTrue();
        assertThat(manager.getArena("farm")).isEmpty();
        assertThat(manager.deleteArena("non_existent")).isFalse();
    }
}
