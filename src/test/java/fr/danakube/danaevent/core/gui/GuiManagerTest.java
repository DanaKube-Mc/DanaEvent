package fr.danakube.danaevent.core.gui;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class GuiManagerTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private GuiManager guiManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        guiManager = plugin.getGuiManager();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should open GUI for player and track open GUI instance")
    void shouldOpenGuiForPlayer() {
        PlayerMock player = server.addPlayer("Steve");
        CustomGui gui = new CustomGui("<green>Main Menu</green>", 27);

        guiManager.openGui(player, gui);

        assertThat(player.getOpenInventory().getTopInventory()).isSameAs(gui.getInventory());
        assertThat(guiManager.getOpenGui(player.getUniqueId())).isPresent().containsSame(gui);
    }

    @Test
    @DisplayName("Should execute button click callback and cancel click event to prevent item theft")
    void shouldExecuteButtonClickAndCancelEvent() {
        PlayerMock player = server.addPlayer("Steve");
        CustomGui gui = new CustomGui("<gold>Shop</gold>", 27);
        AtomicBoolean clicked = new AtomicBoolean(false);

        ItemStack diamond = new ItemStack(Material.DIAMOND);
        gui.setItem(10, diamond, event -> clicked.set(true));

        guiManager.openGui(player, gui);
        InventoryView view = player.getOpenInventory();

        InventoryClickEvent event = new InventoryClickEvent(
            view,
            InventoryType.SlotType.CONTAINER,
            10,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL
        );
        server.getPluginManager().callEvent(event);

        assertThat(clicked.get()).isTrue();
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Should cancel inventory drag events when viewing CustomGui")
    void shouldCancelDragEvents() {
        PlayerMock player = server.addPlayer("Steve");
        CustomGui gui = new CustomGui("<red>Protected Chest</red>", 27);

        guiManager.openGui(player, gui);
        InventoryView view = player.getOpenInventory();

        Map<Integer, ItemStack> newItems = new HashMap<>();
        newItems.put(4, new ItemStack(Material.DIRT));

        InventoryDragEvent dragEvent = new InventoryDragEvent(
            view,
            new ItemStack(Material.DIRT),
            new ItemStack(Material.DIRT),
            false,
            newItems
        );
        server.getPluginManager().callEvent(dragEvent);

        assertThat(dragEvent.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Should navigate pages via pagination buttons click")
    void shouldNavigatePagesViaButtonClick() {
        PlayerMock player = server.addPlayer("Steve");
        CustomGui gui = new CustomGui("<blue>Paginated</blue>", 27);
        gui.setMaxPages(2);

        ItemStack nextBtn = new ItemStack(Material.ARROW);
        gui.setNextPageButton(26, nextBtn);

        guiManager.openGui(player, gui);
        assertThat(gui.getPage()).isEqualTo(1);

        InventoryView view = player.getOpenInventory();
        InventoryClickEvent clickNext = new InventoryClickEvent(
            view,
            InventoryType.SlotType.CONTAINER,
            26,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL
        );
        server.getPluginManager().callEvent(clickNext);

        assertThat(gui.getPage()).isEqualTo(2);
        assertThat(clickNext.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Should clean up and close all open CustomGuis")
    void shouldCleanUpAndCloseAll() {
        PlayerMock player1 = server.addPlayer("Steve");
        PlayerMock player2 = server.addPlayer("Alex");

        CustomGui gui1 = new CustomGui("<green>Gui 1</green>", 27);
        CustomGui gui2 = new CustomGui("<green>Gui 2</green>", 27);

        guiManager.openGui(player1, gui1);
        guiManager.openGui(player2, gui2);

        assertThat(guiManager.getOpenGui(player1.getUniqueId())).isPresent();
        assertThat(guiManager.getOpenGui(player2.getUniqueId())).isPresent();

        guiManager.cleanUp();

        assertThat(guiManager.getOpenGui(player1.getUniqueId())).isEmpty();
        assertThat(guiManager.getOpenGui(player2.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("Should handle inventory close event and untrack player")
    void shouldHandleCloseEvent() {
        PlayerMock player = server.addPlayer("Steve");
        CustomGui gui = new CustomGui("<yellow>Auto Close</yellow>", 27);
        AtomicBoolean closed = new AtomicBoolean(false);
        gui.setOnClose(p -> closed.set(true));

        guiManager.openGui(player, gui);
        assertThat(guiManager.getOpenGui(player.getUniqueId())).isPresent();

        InventoryCloseEvent closeEvent = new InventoryCloseEvent(player.getOpenInventory());
        server.getPluginManager().callEvent(closeEvent);

        assertThat(closed.get()).isTrue();
        assertThat(guiManager.getOpenGui(player.getUniqueId())).isEmpty();
    }
}
