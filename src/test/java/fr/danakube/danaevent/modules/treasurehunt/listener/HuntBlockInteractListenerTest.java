package fr.danakube.danaevent.modules.treasurehunt.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.block.BlockMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.modules.treasurehunt.config.HuntConfig;
import fr.danakube.danaevent.modules.treasurehunt.database.TreasureHuntDatabase;
import fr.danakube.danaevent.modules.treasurehunt.manager.HuntProgressManager;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntMode;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntPathType;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntStep;
import fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress;
import fr.danakube.danaevent.modules.treasurehunt.model.StepTriggerType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HuntBlockInteractListenerTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private DanaEventPlugin plugin;
    private WorldMock world;
    private HuntConfig huntConfig;
    private TreasureHuntDatabase database;
    private HuntProgressManager progressManager;
    private HuntBlockInteractListener listener;
    private Hunt testHunt;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        world = server.addSimpleWorld("world");

        File configFile = new File(tempDir.toFile(), "hunts.yml");
        huntConfig = new HuntConfig(configFile);

        database = new TreasureHuntDatabase(plugin.getDatabaseManager(), Runnable::run);
        database.initTables();

        progressManager = new HuntProgressManager(plugin, huntConfig, database);

        testHunt = huntConfig.createHunt("pyramid", "Pyramide Maudite", HuntMode.SOLO, HuntPathType.LINEAR_STATIC);
        
        HuntStep step1 = new HuntStep(1, StepTriggerType.BLOCK_CLICK);
        step1.setTargetLocation(new Location(world, 10, 64, 20));
        testHunt.addStep(step1);

        HuntStep step2 = new HuntStep(2, StepTriggerType.BLOCK_CLICK);
        step2.setTargetLocation(new Location(world, 30, 64, 50));
        testHunt.addStep(step2);

        huntConfig.saveHunts();

        listener = new HuntBlockInteractListener(plugin, progressManager, huntConfig);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private String getPlainMessage(Component component) {
        return component != null ? PlainTextComponentSerializer.plainText().serialize(component) : "";
    }

    @Test
    @DisplayName("Right clicking the active step block validates step, cancels event, and advances progress")
    void shouldValidateActiveStepOnClick() {
        PlayerMock player = server.addPlayer("Raider");
        progressManager.startHunt(player.getUniqueId(), false, "pyramid");

        BlockMock block = world.getBlockAt(10, 64, 20);
        block.setType(Material.CHEST);

        PlayerInteractEvent event = new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.AIR), block, BlockFace.UP, EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();

        PlayerHuntProgress progress = progressManager.getActiveProgress(player.getUniqueId()).orElseThrow();
        assertThat(progress.getActiveStepNumber()).isEqualTo(2);

        Component msg;
        boolean found = false;
        while ((msg = player.nextComponentMessage()) != null) {
            if (getPlainMessage(msg).contains("Objectif accompli")) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    @DisplayName("Right clicking a future or wrong step block in the hunt cancels event and sends anti-sheep error")
    void shouldRejectFutureStepBlockWithAntiSheepMessage() {
        PlayerMock player = server.addPlayer("LostSheep");
        progressManager.startHunt(player.getUniqueId(), false, "pyramid");

        // Player is at step 1, but clicks step 2's block at (30, 64, 50)
        BlockMock block = world.getBlockAt(30, 64, 50);
        block.setType(Material.CHEST);

        PlayerInteractEvent event = new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.AIR), block, BlockFace.UP, EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();

        PlayerHuntProgress progress = progressManager.getActiveProgress(player.getUniqueId()).orElseThrow();
        assertThat(progress.getActiveStepNumber()).isEqualTo(1); // not advanced

        Component msg;
        boolean found = false;
        while ((msg = player.nextComponentMessage()) != null) {
            if (getPlainMessage(msg).contains("force mystique")) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    @DisplayName("Non-participant clicking a treasure hunt block is blocked from opening it")
    void shouldBlockNonParticipantFromOpeningHuntChest() {
        PlayerMock player = server.addPlayer("Outsider");

        BlockMock block = world.getBlockAt(10, 64, 20);
        block.setType(Material.CHEST);

        PlayerInteractEvent event = new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.AIR), block, BlockFace.UP, EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();
        Component msg;
        boolean found = false;
        while ((msg = player.nextComponentMessage()) != null) {
            if (getPlainMessage(msg).contains("force mystique")) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    @DisplayName("Interacting with unrelated blocks does not cancel event or trigger hunt logic")
    void shouldAllowUnrelatedBlockInteraction() {
        PlayerMock player = server.addPlayer("Miner");
        progressManager.startHunt(player.getUniqueId(), false, "pyramid");

        BlockMock normalBlock = world.getBlockAt(0, 64, 0);
        normalBlock.setType(Material.OAK_DOOR);

        PlayerInteractEvent event = new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.AIR), normalBlock, BlockFace.UP, EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();
    }
}
