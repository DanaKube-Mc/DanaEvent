package fr.danakube.danaevent.modules.chromaticsheep.manager;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameFormat;
import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepArena;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepData;
import fr.danakube.danaevent.modules.chromaticsheep.model.SpecialSheepType;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.entity.Sheep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HerdManagerTest {

    private ServerMock server;
    private WorldMock world;
    private HerdManager herdManager;
    private SheepArena arena;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("herd_world");
        herdManager = new HerdManager();

        arena = new SheepArena("meadow", "Meadow", GameFormat.SOLO, ScoringMode.FINAL_COUNT);
        arena.setSheepCount(10);
        arena.setBounds(new CuboidRegion(
            new Location(world, 0, 60, 0),
            new Location(world, 20, 70, 20)
        ));
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should spawn herd within arena bounds, tag entities, and count colors")
    void shouldSpawnHerdAndTrackEntities() {
        List<Sheep> sheepList = herdManager.spawnHerd(arena, world);

        assertThat(sheepList).hasSize(10);
        assertThat(herdManager.getSheep("meadow")).hasSize(10);

        for (Sheep s : sheepList) {
            assertThat(SheepData.isGameSheep(s)).isTrue();
            assertThat(SheepData.getArenaId(s)).contains("meadow");
            assertThat(arena.getBounds().contains(s.getLocation())).isTrue();
            assertThat(s.isAdult()).isTrue();
            assertThat(s.getAgeLock()).isTrue();
            assertThat(s.canBreed()).isFalse();
        }

        // Color counts
        Map<DyeColor, Integer> counts = herdManager.countColors("meadow", world);
        int totalTracked = counts.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(totalTracked).isEqualTo(10);
    }

    @Test
    @DisplayName("Should configure special sheep visuals and PDC")
    void shouldConfigureSpecialSheep() {
        Sheep s1 = (Sheep) world.spawnEntity(new Location(world, 5, 64, 5), org.bukkit.entity.EntityType.SHEEP);
        Sheep s2 = (Sheep) world.spawnEntity(new Location(world, 5, 64, 5), org.bukkit.entity.EntityType.SHEEP);
        Sheep s3 = (Sheep) world.spawnEntity(new Location(world, 5, 64, 5), org.bukkit.entity.EntityType.SHEEP);

        herdManager.configureSheepType(s1, SpecialSheepType.GOLDEN, "arena_x");
        assertThat(s1.getColor()).isEqualTo(DyeColor.YELLOW);
        assertThat(s1.isGlowing()).isTrue();
        assertThat(SheepData.getSpecialType(s1)).isEqualTo(SpecialSheepType.GOLDEN);

        herdManager.configureSheepType(s2, SpecialSheepType.RAINBOW, "arena_x");
        assertThat(s2.getCustomName()).isEqualTo("jeb_");
        assertThat(SheepData.getSpecialType(s2)).isEqualTo(SpecialSheepType.RAINBOW);

        herdManager.configureSheepType(s3, SpecialSheepType.TRICKSTER, "arena_x");
        assertThat(s3.getColor()).isEqualTo(DyeColor.BLACK);
        assertThat(SheepData.getSpecialType(s3)).isEqualTo(SpecialSheepType.TRICKSTER);
    }

    @Test
    @DisplayName("Should find nearby game sheep within radius")
    void shouldFindNearbyGameSheep() {
        herdManager.spawnHerd(arena, world);

        Location searchCenter = new Location(world, 10, 64, 10);
        List<Sheep> nearby = herdManager.getNearbyGameSheep(searchCenter, 30.0, "meadow");
        assertThat(nearby).isNotEmpty();

        List<Sheep> farAway = herdManager.getNearbyGameSheep(new Location(world, 500, 64, 500), 5.0, "meadow");
        assertThat(farAway).isEmpty();
    }

    @Test
    @DisplayName("CleanUp should remove all entities without orphan mobs")
    void shouldCleanUpEntities() {
        List<Sheep> sheepList = herdManager.spawnHerd(arena, world);
        assertThat(sheepList).hasSize(10);

        herdManager.cleanUpArena("meadow", world);
        assertThat(herdManager.getSheep("meadow")).isEmpty();

        for (Sheep s : sheepList) {
            assertThat(s.isValid()).isFalse();
        }
    }
}
