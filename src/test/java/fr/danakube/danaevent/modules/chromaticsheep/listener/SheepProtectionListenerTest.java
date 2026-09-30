package fr.danakube.danaevent.modules.chromaticsheep.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.modules.chromaticsheep.item.PaintBombItem;
import fr.danakube.danaevent.modules.chromaticsheep.item.PaintBrushItem;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepData;
import fr.danakube.danaevent.modules.chromaticsheep.model.SpecialSheepType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Sheep;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SheepProtectionListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private SheepProtectionListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("protect_world");
        player = server.addPlayer();
        listener = new SheepProtectionListener();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should cancel damage on game sheep but allow damage on normal sheep")
    void shouldCancelDamageOnGameSheep() {
        Sheep gameSheep = (Sheep) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.SHEEP);
        SheepData.tagSheep(gameSheep, "arena_1", SpecialSheepType.NORMAL);

        Sheep normalSheep = (Sheep) world.spawnEntity(new Location(world, 10, 64, 10), EntityType.SHEEP);

        EntityDamageEvent gameDmg = new EntityDamageEvent(gameSheep, EntityDamageEvent.DamageCause.CONTACT, 5.0);
        listener.onEntityDamage(gameDmg);
        assertThat(gameDmg.isCancelled()).isTrue();

        EntityDamageEvent normalDmg = new EntityDamageEvent(normalSheep, EntityDamageEvent.DamageCause.CONTACT, 5.0);
        listener.onEntityDamage(normalDmg);
        assertThat(normalDmg.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Should cancel shearing on game sheep")
    void shouldCancelShearing() {
        Sheep gameSheep = (Sheep) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.SHEEP);
        SheepData.tagSheep(gameSheep, "arena_1", SpecialSheepType.NORMAL);

        PlayerShearEntityEvent event = new PlayerShearEntityEvent(
            player,
            gameSheep,
            new ItemStack(Material.SHEARS),
            EquipmentSlot.HAND,
            java.util.List.of()
        );
        listener.onPlayerShear(event);
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Should cancel interacting with breeding items or shears on game sheep")
    void shouldCancelBreedingInteractions() {
        Sheep gameSheep = (Sheep) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.SHEEP);
        SheepData.tagSheep(gameSheep, "arena_1", SpecialSheepType.NORMAL);

        // Player holds wheat
        player.getInventory().setItemInMainHand(new ItemStack(Material.WHEAT));

        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, gameSheep, EquipmentSlot.HAND);
        listener.onPlayerInteractEntity(event);
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Should cancel dropping PaintBrush or PaintBomb")
    void shouldCancelDroppingTools() {
        ItemStack brush = PaintBrushItem.createItem();
        Item itemEntity = world.dropItem(player.getLocation(), brush);

        PlayerDropItemEvent dropBrush = new PlayerDropItemEvent(player, itemEntity);
        listener.onPlayerDropItem(dropBrush);
        assertThat(dropBrush.isCancelled()).isTrue();

        ItemStack bomb = PaintBombItem.createItem();
        Item bombEntity = world.dropItem(player.getLocation(), bomb);

        PlayerDropItemEvent dropBomb = new PlayerDropItemEvent(player, bombEntity);
        listener.onPlayerDropItem(dropBomb);
        assertThat(dropBomb.isCancelled()).isTrue();

        // Dropping normal item
        Item normalItem = world.dropItem(player.getLocation(), new ItemStack(Material.STONE));
        PlayerDropItemEvent dropNormal = new PlayerDropItemEvent(player, normalItem);
        listener.onPlayerDropItem(dropNormal);
        assertThat(dropNormal.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Should cancel clicking or moving PaintBrush in inventory")
    void shouldCancelInventoryClick() {
        ItemStack brush = PaintBrushItem.createItem();
        InventoryView view = player.getOpenInventory();

        InventoryClickEvent click = new InventoryClickEvent(
            view,
            InventoryType.SlotType.CONTAINER,
            0,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL
        );
        click.setCurrentItem(brush);

        listener.onInventoryClick(click);
        assertThat(click.isCancelled()).isTrue();
    }
}
