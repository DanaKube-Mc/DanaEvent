package fr.danakube.danaevent.modules.deacoudre.model;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DacArenaTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("dac_test_world");
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Enums should parse valid names, aliases and fallback properly")
    void testEnums() {
        assertThat(JumpMode.fromString("turn")).isEqualTo(JumpMode.TURN_BY_TURN);
        assertThat(JumpMode.fromString("wave")).isEqualTo(JumpMode.SIMULTANEOUS_WAVE);
        assertThat(JumpMode.fromString("invalid")).isEqualTo(JumpMode.TURN_BY_TURN);

        assertThat(DacGameFormat.fromString("solo")).isEqualTo(DacGameFormat.SOLO);
        assertThat(DacGameFormat.fromString("team")).isEqualTo(DacGameFormat.TEAM);
        assertThat(DacGameFormat.fromString("invalid")).isEqualTo(DacGameFormat.SOLO);

        assertThat(DacTeamLifeMode.fromString("shared")).isEqualTo(DacTeamLifeMode.SHARED_POOL);
        assertThat(DacTeamLifeMode.fromString("last")).isEqualTo(DacTeamLifeMode.LAST_STANDING);
        assertThat(DacTeamLifeMode.fromString("invalid")).isEqualTo(DacTeamLifeMode.LAST_STANDING);
    }

    @Test
    @DisplayName("DacArena should enforce valid properties and calculate isReady correctly")
    void testDacArenaModel() {
        DacArena arena = new DacArena("olympic", "Bassin Olympique", DacGameFormat.SOLO, JumpMode.TURN_BY_TURN);
        assertThat(arena.getId()).isEqualTo("olympic");
        assertThat(arena.getDisplayName()).isEqualTo("Bassin Olympique");
        assertThat(arena.getInitialLives()).isEqualTo(3);
        assertThat(arena.getJumpTimeSeconds()).isEqualTo(15);
        assertThat(arena.isEnabled()).isTrue();
        assertThat(arena.isReady()).isFalse();

        CuboidRegion pool = new CuboidRegion(world.getName(), 0, 50, 0, 10, 55, 10);
        Location diving = new Location(world, 5, 80, 5);
        Location lobby = new Location(world, 5, 60, -10);

        arena.setPoolRegion(pool);
        assertThat(arena.isReady()).isFalse();

        arena.setDivingLocation(diving);
        assertThat(arena.isReady()).isFalse();

        arena.setLobbyLocation(lobby);
        assertThat(arena.isReady()).isTrue();

        assertThatThrownBy(() -> arena.setInitialLives(0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> arena.setJumpTimeSeconds(-5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DacRecord and DacPoolBlock should accurately encapsulate attributes")
    void testRecordsAndPoolBlock() {
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();
        DacRecord record = new DacRecord("olympic", uuid, false, 5, 20, 2, "2026-09", now);

        assertThat(record.arenaId()).isEqualTo("olympic");
        assertThat(record.holderUuid()).isEqualTo(uuid);
        assertThat(record.isTeam()).isFalse();
        assertThat(record.wins()).isEqualTo(5);
        assertThat(record.successfulJumps()).isEqualTo(20);
        assertThat(record.perfectDacs()).isEqualTo(2);
        assertThat(record.periodMonth()).isEqualTo("2026-09");
        assertThat(record.updatedAt()).isEqualTo(now);

        BlockData waterData = Material.WATER.createBlockData();
        DacPoolBlock poolBlock = new DacPoolBlock(1, 64, 2, waterData);
        assertThat(poolBlock.x()).isEqualTo(1);
        assertThat(poolBlock.y()).isEqualTo(64);
        assertThat(poolBlock.z()).isEqualTo(2);
        assertThat(poolBlock.originalData()).isEqualTo(waterData);
    }
}
