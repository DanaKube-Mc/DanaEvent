package fr.danakube.danaevent.modules.chromaticsheep.model;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SheepArenaTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("arena_world");
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should create SheepArena with default values and check readiness")
    void shouldCreateArenaAndCheckReadiness() {
        SheepArena arena = new SheepArena("pasture_1", "Pâturage Vert");

        assertThat(arena.getId()).isEqualTo("pasture_1");
        assertThat(arena.getDisplayName()).isEqualTo("Pâturage Vert");
        assertThat(arena.getFormat()).isEqualTo(GameFormat.SOLO);
        assertThat(arena.getScoringMode()).isEqualTo(ScoringMode.FINAL_COUNT);
        assertThat(arena.getSheepCount()).isEqualTo(40);
        assertThat(arena.getDurationSeconds()).isEqualTo(120);
        assertThat(arena.isEnabled()).isTrue();
        assertThat(arena.getPlayerSpawns()).isEmpty();
        assertThat(arena.getBounds()).isNull();
        assertThat(arena.isReady()).isFalse();

        // Configure bounds & spawn
        CuboidRegion region = new CuboidRegion(
            new Location(world, 0, 60, 0),
            new Location(world, 30, 75, 30)
        );
        arena.setBounds(region);
        assertThat(arena.isReady()).isFalse(); // still missing spawns

        Location spawn = new Location(world, 15, 61, 15);
        arena.addPlayerSpawn(spawn);
        assertThat(arena.getPlayerSpawns()).hasSize(1);
        assertThat(arena.isReady()).isTrue();

        // Clear spawns
        arena.clearPlayerSpawns();
        assertThat(arena.getPlayerSpawns()).isEmpty();
        assertThat(arena.isReady()).isFalse();
    }

    @Test
    @DisplayName("Should manage player spawns list correctly")
    void shouldManagePlayerSpawns() {
        SheepArena arena = new SheepArena("arena_spawns", "Spawns Test");
        Location loc1 = new Location(world, 10, 64, 10);
        Location loc2 = new Location(world, 20, 64, 20);

        arena.setPlayerSpawns(List.of(loc1, loc2));
        assertThat(arena.getPlayerSpawns()).hasSize(2);

        // Remove spawn at index
        assertThat(arena.removePlayerSpawn(0)).isTrue();
        assertThat(arena.getPlayerSpawns()).hasSize(1);
        assertThat(arena.removePlayerSpawn(99)).isFalse();

        // Modifying returned list should throw UnsupportedOperationException
        assertThatThrownBy(() -> arena.getPlayerSpawns().add(new Location(world, 0, 0, 0)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should validate sheepCount and durationSeconds")
    void shouldValidateNumericAttributes() {
        SheepArena arena = new SheepArena("arena_limits", "Limits");

        arena.setSheepCount(50);
        assertThat(arena.getSheepCount()).isEqualTo(50);
        assertThatThrownBy(() -> arena.setSheepCount(0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> arena.setSheepCount(-5))
            .isInstanceOf(IllegalArgumentException.class);

        arena.setDurationSeconds(180);
        assertThat(arena.getDurationSeconds()).isEqualTo(180);
        assertThatThrownBy(() -> arena.setDurationSeconds(0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> arena.setDurationSeconds(-10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Enums should parse safely with default fallbacks")
    void shouldParseEnumsSafely() {
        assertThat(GameFormat.fromString("team")).isEqualTo(GameFormat.TEAM);
        assertThat(GameFormat.fromString("SOLO")).isEqualTo(GameFormat.SOLO);
        assertThat(GameFormat.fromString("unknown")).isEqualTo(GameFormat.SOLO);
        assertThat(GameFormat.fromString(null)).isEqualTo(GameFormat.SOLO);

        assertThat(ScoringMode.fromString("domination_tick")).isEqualTo(ScoringMode.DOMINATION_TICK);
        assertThat(ScoringMode.fromString("action_score")).isEqualTo(ScoringMode.ACTION_SCORE);
        assertThat(ScoringMode.fromString("final_count")).isEqualTo(ScoringMode.FINAL_COUNT);
        assertThat(ScoringMode.fromString(null)).isEqualTo(ScoringMode.FINAL_COUNT);

        assertThat(SpecialSheepType.fromString("golden")).isEqualTo(SpecialSheepType.GOLDEN);
        assertThat(SpecialSheepType.fromString("rainbow")).isEqualTo(SpecialSheepType.RAINBOW);
        assertThat(SpecialSheepType.fromString("trickster")).isEqualTo(SpecialSheepType.TRICKSTER);
        assertThat(SpecialSheepType.fromString("normal")).isEqualTo(SpecialSheepType.NORMAL);
        assertThat(SpecialSheepType.fromString(null)).isEqualTo(SpecialSheepType.NORMAL);
    }

    @Test
    @DisplayName("SheepRecord should encapsulate fields accurately")
    void shouldEncapsulateSheepRecord() {
        UUID holder = UUID.randomUUID();
        Instant now = Instant.now();
        SheepRecord record = new SheepRecord(
            1L,
            "arena_1",
            holder,
            false,
            250,
            18,
            ScoringMode.FINAL_COUNT,
            "2026-09",
            now
        );

        assertThat(record.id()).isEqualTo(1L);
        assertThat(record.arenaId()).isEqualTo("arena_1");
        assertThat(record.holderUuid()).isEqualTo(holder);
        assertThat(record.isTeam()).isFalse();
        assertThat(record.scorePoints()).isEqualTo(250);
        assertThat(record.sheepCount()).isEqualTo(18);
        assertThat(record.scoringMode()).isEqualTo(ScoringMode.FINAL_COUNT);
        assertThat(record.periodMonth()).isEqualTo("2026-09");
        assertThat(record.playedAt()).isEqualTo(now);
    }
}
