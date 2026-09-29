package fr.danakube.danaevent.core.selection;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionManagerTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private SelectionManager selectionManager;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        selectionManager = plugin.getSelectionManager();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Nested
    @DisplayName("Wand Creation & PDC Validation")
    class WandTests {

        @Test
        @DisplayName("createWandItem should create a Blaze Rod with MiniMessage meta and PDC tag")
        void shouldCreateWandItemProperly() {
            ItemStack wand = selectionManager.createWandItem();

            assertThat(wand).isNotNull();
            assertThat(wand.getType()).isEqualTo(Material.BLAZE_ROD);
            assertThat(wand.hasItemMeta()).isTrue();

            ItemMeta meta = wand.getItemMeta();
            assertThat(meta).isNotNull();
            assertThat(meta.displayName()).isNotNull();
            assertThat(meta.lore()).isNotNull().hasSize(2);

            Byte tag = meta.getPersistentDataContainer().get(selectionManager.getWandKey(), PersistentDataType.BYTE);
            assertThat(tag).isEqualTo((byte) 1);
            assertThat(selectionManager.isWand(wand)).isTrue();
        }

        @Test
        @DisplayName("isWand should reject items without PDC tag even if identically named via anvil")
        void shouldRejectAnvilRenamedItemWithoutPdc() {
            // Normal blaze rod
            ItemStack plainBlazeRod = new ItemStack(Material.BLAZE_ROD);
            assertThat(selectionManager.isWand(plainBlazeRod)).isFalse();

            // Renamed blaze rod (anvil spoofing)
            ItemStack fakeWand = new ItemStack(Material.BLAZE_ROD);
            ItemMeta fakeMeta = fakeWand.getItemMeta();
            fakeMeta.displayName(Component.text("Bâton de Sélection"));
            fakeWand.setItemMeta(fakeMeta);

            assertThat(selectionManager.isWand(fakeWand)).isFalse();
        }

        @Test
        @DisplayName("isWand should handle null and air safely")
        void shouldHandleNullAndAir() {
            assertThat(selectionManager.isWand(null)).isFalse();
            assertThat(selectionManager.isWand(new ItemStack(Material.AIR))).isFalse();
        }

        @Test
        @DisplayName("isWand should reject items with different PDC keys or wrong values")
        void shouldRejectWrongPdc() {
            ItemStack item = new ItemStack(Material.BLAZE_ROD);
            ItemMeta meta = item.getItemMeta();

            NamespacedKey otherKey = new NamespacedKey("fakeplugin", "selection_wand");
            meta.getPersistentDataContainer().set(otherKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);

            assertThat(selectionManager.isWand(item)).isFalse();

            // Wrong value
            meta.getPersistentDataContainer().set(selectionManager.getWandKey(), PersistentDataType.BYTE, (byte) 0);
            item.setItemMeta(meta);

            assertThat(selectionManager.isWand(item)).isFalse();
        }

        @Test
        @DisplayName("giveWand should add wand to player's inventory")
        void shouldGiveWandToPlayer() {
            PlayerMock player = server.addPlayer();
            assertThat(player.getInventory().contains(Material.BLAZE_ROD)).isFalse();

            selectionManager.giveWand(player);

            assertThat(player.getInventory().contains(Material.BLAZE_ROD)).isTrue();
            ItemStack item = player.getInventory().getItem(0);
            assertThat(selectionManager.isWand(item)).isTrue();
        }
    }

    @Nested
    @DisplayName("Player Selections & Cuboid Construction")
    class SelectionTests {

        private UUID player1;
        private UUID player2;

        @BeforeEach
        void initPlayers() {
            player1 = UUID.randomUUID();
            player2 = UUID.randomUUID();
        }

        @Test
        @DisplayName("Should independently track Pos1 and Pos2 per player")
        void shouldTrackPositionsIndependently() {
            Location loc1 = new Location(world, 10, 64, 10);
            Location loc2 = new Location(world, 20, 70, 20);

            selectionManager.setPos1(player1, loc1);
            selectionManager.setPos2(player1, loc2);

            assertThat(selectionManager.getPos1(player1)).isEqualTo(loc1);
            assertThat(selectionManager.getPos2(player1)).isEqualTo(loc2);

            // Player 2 has nothing set yet
            assertThat(selectionManager.getPos1(player2)).isNull();
            assertThat(selectionManager.getPos2(player2)).isNull();
        }

        @Test
        @DisplayName("Setting location to null should remove it")
        void shouldRemoveLocationWhenNull() {
            Location loc = new Location(world, 5, 5, 5);
            selectionManager.setPos1(player1, loc);
            assertThat(selectionManager.getPos1(player1)).isNotNull();

            selectionManager.setPos1(player1, null);
            assertThat(selectionManager.getPos1(player1)).isNull();
        }

        @Test
        @DisplayName("Should return empty region when selection is incomplete or in different worlds")
        void shouldHandleIncompleteOrMismatchedSelections() {
            Location loc1 = new Location(world, 0, 64, 0);
            selectionManager.setPos1(player1, loc1);

            // Only pos1 set
            assertThat(selectionManager.getRegion(player1)).isEmpty();

            // Set pos2 in another world
            WorldMock nether = server.addSimpleWorld("world_nether");
            Location loc2 = new Location(nether, 10, 70, 10);
            selectionManager.setPos2(player1, loc2);

            assertThat(selectionManager.getRegion(player1)).isEmpty();
        }

        @Test
        @DisplayName("Should construct valid CuboidRegion when both positions are in the same world")
        void shouldConstructValidCuboidRegion() {
            Location loc1 = new Location(world, 0, 60, 0);
            Location loc2 = new Location(world, 10, 70, 10);

            selectionManager.setPos1(player1, loc1);
            selectionManager.setPos2(player1, loc2);

            Optional<CuboidRegion> optRegion = selectionManager.getRegion(player1);
            assertThat(optRegion).isPresent();

            CuboidRegion region = optRegion.get();
            assertThat(region.getWorldName()).isEqualTo("world");
            assertThat(region.getMinX()).isEqualTo(0);
            assertThat(region.getMaxX()).isEqualTo(10);
            assertThat(region.getMinY()).isEqualTo(60);
            assertThat(region.getMaxY()).isEqualTo(70);
            assertThat(region.getVolume()).isEqualTo(11L * 11L * 11L);
        }

        @Test
        @DisplayName("clearSelection should remove both positions for a single player")
        void shouldClearSelectionForPlayer() {
            Location loc = new Location(world, 0, 0, 0);
            selectionManager.setPos1(player1, loc);
            selectionManager.setPos2(player1, loc);
            selectionManager.setPos1(player2, loc);

            selectionManager.clearSelection(player1);

            assertThat(selectionManager.getPos1(player1)).isNull();
            assertThat(selectionManager.getPos2(player1)).isNull();
            assertThat(selectionManager.getPos1(player2)).isNotNull();
        }

        @Test
        @DisplayName("cleanUp should clear all selections across all players")
        void shouldCleanUpAllSelections() {
            Location loc = new Location(world, 0, 0, 0);
            selectionManager.setPos1(player1, loc);
            selectionManager.setPos2(player1, loc);
            selectionManager.setPos1(player2, loc);

            selectionManager.cleanUp();

            assertThat(selectionManager.getPos1(player1)).isNull();
            assertThat(selectionManager.getPos2(player1)).isNull();
            assertThat(selectionManager.getPos1(player2)).isNull();
        }
    }
}
