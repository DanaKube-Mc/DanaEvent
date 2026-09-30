package fr.danakube.danaevent.modules.deacoudre.manager;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameFormat;
import fr.danakube.danaevent.modules.deacoudre.model.JumpMode;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DacPoolManagerTest {

    private ServerMock server;
    private WorldMock world;
    private DacPoolManager poolManager;
    private DacArena arena;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("dac_pool_world");
        poolManager = new DacPoolManager();

        arena = new DacArena("pool_test", "Test Pool", DacGameFormat.SOLO, JumpMode.TURN_BY_TURN);
        arena.setPoolRegion(new CuboidRegion(world.getName(), -2, 50, -2, 2, 50, 2));

        // Fill pool region with water
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(x, 50, z).setType(Material.WATER);
            }
        }
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should detect water, wool, inside pool bounds and count remaining water")
    void testPoolBasicChecks() {
        assertThat(poolManager.isInsidePool(world.getBlockAt(0, 50, 0).getLocation(), arena)).isTrue();
        assertThat(poolManager.isInsidePool(world.getBlockAt(10, 50, 10).getLocation(), arena)).isFalse();

        Block waterBlock = world.getBlockAt(0, 50, 0);
        assertThat(poolManager.isWater(waterBlock)).isTrue();
        assertThat(poolManager.isWool(waterBlock)).isFalse();

        poolManager.convertToWool(waterBlock, DyeColor.RED);
        assertThat(waterBlock.getType()).isEqualTo(Material.RED_WOOL);
        assertThat(poolManager.isWool(waterBlock)).isTrue();
        assertThat(poolManager.isWater(waterBlock)).isFalse();

        int remaining = poolManager.getRemainingWaterCount(arena, world);
        // 5x5 = 25 total, 1 converted to wool = 24 remaining
        assertThat(remaining).isEqualTo(24);
    }

    @Test
    @DisplayName("Should detect Perfect DAC (1x1 water surrounded by 4 wool blocks)")
    void testPerfectDacDetection() {
        Block center = world.getBlockAt(0, 50, 0);
        assertThat(poolManager.isPerfectDac(center)).isFalse();

        // Surround with 3 wool blocks -> still false
        world.getBlockAt(0, 50, -1).setType(Material.BLUE_WOOL); // North
        world.getBlockAt(0, 50, 1).setType(Material.YELLOW_WOOL); // South
        world.getBlockAt(1, 50, 0).setType(Material.GREEN_WOOL);  // East
        assertThat(poolManager.isPerfectDac(center)).isFalse();

        // 4th wool block -> Perfect DAC!
        world.getBlockAt(-1, 50, 0).setType(Material.RED_WOOL);  // West
        assertThat(poolManager.isPerfectDac(center)).isTrue();
    }

    @Test
    @DisplayName("Should capture pool snapshot and cleanly rollback all modified wool blocks")
    void testSnapshotAndRollback() {
        poolManager.captureSnapshot(arena, world);

        // Turn blocks into wool
        world.getBlockAt(-1, 50, 0).setType(Material.RED_WOOL);
        world.getBlockAt(0, 50, 0).setType(Material.BLUE_WOOL);
        world.getBlockAt(1, 50, 0).setType(Material.LIME_WOOL);

        assertThat(poolManager.getRemainingWaterCount(arena, world)).isEqualTo(22);

        // Rollback
        poolManager.rollbackPool(arena, world);

        assertThat(world.getBlockAt(-1, 50, 0).getType()).isEqualTo(Material.WATER);
        assertThat(world.getBlockAt(0, 50, 0).getType()).isEqualTo(Material.WATER);
        assertThat(world.getBlockAt(1, 50, 0).getType()).isEqualTo(Material.WATER);
        assertThat(poolManager.getRemainingWaterCount(arena, world)).isEqualTo(25);
    }
}
