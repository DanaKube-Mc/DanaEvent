package fr.danakube.danaevent.core.selection;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.block.BlockMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WandListenerTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private SelectionManager selectionManager;
    private WorldMock world;
    private PlayerMock player;
    private ItemStack wand;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        selectionManager = plugin.getSelectionManager();
        world = server.addSimpleWorld("world");
        player = server.addPlayer();
        wand = selectionManager.createWandItem();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private String getPlainMessage(Component component) {
        if (component == null) {
            return null;
        }
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Nested
    @DisplayName("Block Selection via Clicks")
    class InteractionTests {

        @Test
        @DisplayName("Left-clicking a block with wand should set Pos1, cancel event, and notify player")
        void shouldSetPos1OnLeftClick() {
            player.getInventory().setItemInMainHand(wand);
            BlockMock block = world.getBlockAt(10, 64, 20);

            PlayerInteractEvent event = new PlayerInteractEvent(
                player,
                Action.LEFT_CLICK_BLOCK,
                wand,
                block,
                BlockFace.UP,
                EquipmentSlot.HAND
            );
            server.getPluginManager().callEvent(event);

            assertThat(event.isCancelled()).isTrue();
            assertThat(selectionManager.getPos1(player.getUniqueId()))
                .isEqualTo(new Location(world, 10, 64, 20));

            Component msg = player.nextComponentMessage();
            assertThat(msg).isNotNull();
            String plain = getPlainMessage(msg);
            assertThat(plain).contains("Position 1", "10, 64, 20", "world");
        }

        @Test
        @DisplayName("Right-clicking a block with wand should set Pos2, cancel event, and notify player")
        void shouldSetPos2OnRightClick() {
            player.getInventory().setItemInMainHand(wand);
            BlockMock block = world.getBlockAt(30, 70, 40);

            PlayerInteractEvent event = new PlayerInteractEvent(
                player,
                Action.RIGHT_CLICK_BLOCK,
                wand,
                block,
                BlockFace.UP,
                EquipmentSlot.HAND
            );
            server.getPluginManager().callEvent(event);

            assertThat(event.isCancelled()).isTrue();
            assertThat(selectionManager.getPos2(player.getUniqueId()))
                .isEqualTo(new Location(world, 30, 70, 40));

            Component msg = player.nextComponentMessage();
            assertThat(msg).isNotNull();
            String plain = getPlainMessage(msg);
            assertThat(plain).contains("Position 2", "30, 70, 40", "world");
        }

        @Test
        @DisplayName("Setting Pos2 when Pos1 is already defined should also send volume message")
        void shouldSendVolumeWhenBothPositionsSet() {
            player.getInventory().setItemInMainHand(wand);

            // Left click Pos1 at (0, 0, 0)
            BlockMock block1 = world.getBlockAt(0, 0, 0);
            PlayerInteractEvent event1 = new PlayerInteractEvent(
                player, Action.LEFT_CLICK_BLOCK, wand, block1, BlockFace.UP, EquipmentSlot.HAND
            );
            server.getPluginManager().callEvent(event1);
            player.nextComponentMessage(); // consume pos1 message

            // Right click Pos2 at (2, 2, 2)
            BlockMock block2 = world.getBlockAt(2, 2, 2);
            PlayerInteractEvent event2 = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, wand, block2, BlockFace.UP, EquipmentSlot.HAND
            );
            server.getPluginManager().callEvent(event2);

            Component pos2Msg = player.nextComponentMessage();
            Component volumeMsg = player.nextComponentMessage();

            assertThat(getPlainMessage(pos2Msg)).contains("Position 2", "2, 2, 2");
            assertThat(volumeMsg).isNotNull();
            String plainVolume = getPlainMessage(volumeMsg);
            assertThat(plainVolume).contains("27 blocs", "3x3x3");
        }

        @Test
        @DisplayName("Setting Pos1 when Pos2 is already defined should also send volume message")
        void shouldSendVolumeWhenPos1SetAfterPos2() {
            player.getInventory().setItemInMainHand(wand);

            // Right click Pos2 first at (1, 1, 1)
            BlockMock block2 = world.getBlockAt(1, 1, 1);
            PlayerInteractEvent event2 = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, wand, block2, BlockFace.UP, EquipmentSlot.HAND
            );
            server.getPluginManager().callEvent(event2);
            player.nextComponentMessage(); // consume pos2 message

            // Left click Pos1 at (0, 0, 0)
            BlockMock block1 = world.getBlockAt(0, 0, 0);
            PlayerInteractEvent event1 = new PlayerInteractEvent(
                player, Action.LEFT_CLICK_BLOCK, wand, block1, BlockFace.UP, EquipmentSlot.HAND
            );
            server.getPluginManager().callEvent(event1);

            Component pos1Msg = player.nextComponentMessage();
            Component volumeMsg = player.nextComponentMessage();

            assertThat(getPlainMessage(pos1Msg)).contains("Position 1", "0, 0, 0");
            assertThat(volumeMsg).isNotNull();
            String plainVolume = getPlainMessage(volumeMsg);
            assertThat(plainVolume).contains("8 blocs", "2x2x2");
        }

        @Test
        @DisplayName("Interacting with non-wand items should not cancel event or alter selections")
        void shouldIgnoreNonWandItems() {
            ItemStack stick = new ItemStack(Material.STICK);
            player.getInventory().setItemInMainHand(stick);
            BlockMock block = world.getBlockAt(5, 5, 5);

            PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.LEFT_CLICK_BLOCK, stick, block, BlockFace.UP, EquipmentSlot.HAND
            );
            server.getPluginManager().callEvent(event);

            assertThat(event.isCancelled()).isFalse();
            assertThat(selectionManager.getPos1(player.getUniqueId())).isNull();
            assertThat(player.nextComponentMessage()).isNull();
        }

        @Test
        @DisplayName("Clicking air with wand should cancel event without throwing or altering selection")
        void shouldCancelAirInteractionSafely() {
            player.getInventory().setItemInMainHand(wand);

            PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.LEFT_CLICK_AIR, wand, null, BlockFace.UP, EquipmentSlot.HAND
            );
            server.getPluginManager().callEvent(event);

            assertThat(event.isCancelled()).isTrue();
            assertThat(selectionManager.getPos1(player.getUniqueId())).isNull();
        }

        @Test
        @DisplayName("Off-hand wand interactions should cancel event but not duplicate position logic")
        void shouldIgnoreOffHandDuplication() {
            player.getInventory().setItemInOffHand(wand);
            BlockMock block = world.getBlockAt(10, 64, 20);

            PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.LEFT_CLICK_BLOCK, wand, block, BlockFace.UP, EquipmentSlot.OFF_HAND
            );
            server.getPluginManager().callEvent(event);

            assertThat(event.isCancelled()).isTrue();
            assertThat(selectionManager.getPos1(player.getUniqueId())).isNull();
        }
    }

    @Nested
    @DisplayName("Block Break Prevention")
    class BlockBreakTests {

        @Test
        @DisplayName("Player holding wand in main hand should be prevented from breaking blocks")
        void shouldPreventBlockBreakWithMainHandWand() {
            player.getInventory().setItemInMainHand(wand);
            BlockMock block = world.getBlockAt(10, 64, 20);

            BlockBreakEvent event = new BlockBreakEvent(block, player);
            server.getPluginManager().callEvent(event);

            assertThat(event.isCancelled()).isTrue();
        }

        @Test
        @DisplayName("Player holding wand in off-hand should be prevented from breaking blocks")
        void shouldPreventBlockBreakWithOffHandWand() {
            player.getInventory().setItemInOffHand(wand);
            BlockMock block = world.getBlockAt(10, 64, 20);

            BlockBreakEvent event = new BlockBreakEvent(block, player);
            server.getPluginManager().callEvent(event);

            assertThat(event.isCancelled()).isTrue();
        }

        @Test
        @DisplayName("Player without wand should be able to break blocks normally")
        void shouldAllowBlockBreakWithoutWand() {
            player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
            BlockMock block = world.getBlockAt(10, 64, 20);

            BlockBreakEvent event = new BlockBreakEvent(block, player);
            server.getPluginManager().callEvent(event);

            assertThat(event.isCancelled()).isFalse();
        }
    }
}
