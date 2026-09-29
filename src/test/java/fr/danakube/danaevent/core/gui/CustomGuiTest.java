package fr.danakube.danaevent.core.gui;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomGuiTest {

    private ServerMock server;
    private DanaEventPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should create CustomGui with MiniMessage title and valid sizes")
    void shouldCreateCustomGuiWithValidSizes() {
        CustomGui gui = new CustomGui("<gold><bold>Menu Principal</bold></gold>", 27);
        assertThat(gui.getSize()).isEqualTo(27);
        assertThat(gui.getInventory().getSize()).isEqualTo(27);
        assertThat(gui.isCancelClicks()).isTrue();

        Component customComp = MiniMessage.miniMessage().deserialize("<red>Event List</red>");
        CustomGui gui2 = new CustomGui(customComp, 54);
        assertThat(gui2.getSize()).isEqualTo(54);

        assertThatThrownBy(() -> new CustomGui("<red>Invalid</red>", 12))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CustomGui("<red>Invalid</red>", 5))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CustomGui("<red>Invalid</red>", 63))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should set, get, remove and clear items in CustomGui")
    void shouldManageItems() {
        CustomGui gui = new CustomGui("<green>Test GUI</green>", 27);
        ItemStack diamond = new ItemStack(Material.DIAMOND);

        gui.setItem(13, diamond);
        assertThat(gui.getItem(13)).isNotNull();
        assertThat(gui.getItem(13).getItemStack()).isEqualTo(diamond);
        assertThat(gui.getInventory().getItem(13)).isEqualTo(diamond);

        gui.removeItem(13);
        assertThat(gui.getItem(13)).isNull();
        assertThat(gui.getInventory().getItem(13)).isNull();

        gui.setItem(0, new ItemStack(Material.GOLD_INGOT));
        gui.setItem(1, new ItemStack(Material.IRON_INGOT));
        assertThat(gui.getItems()).hasSize(2);

        gui.clear();
        assertThat(gui.getItems()).isEmpty();
        assertThat(gui.getInventory().getItem(0)).isNull();
        assertThat(gui.getInventory().getItem(1)).isNull();

        assertThatThrownBy(() -> gui.setItem(27, diamond))
            .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("Should fill empty slots with filler item without overwriting existing items")
    void shouldFillEmptySlots() {
        CustomGui gui = new CustomGui("<yellow>Fill Test</yellow>", 9);
        ItemStack emerald = new ItemStack(Material.EMERALD);
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);

        gui.setItem(4, emerald);
        gui.fill(filler);

        assertThat(gui.getInventory().getItem(4)).isEqualTo(emerald);
        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                assertThat(gui.getInventory().getItem(i)).isEqualTo(filler);
                assertThat(gui.getItem(i)).isNotNull();
            }
        }
    }

    @Test
    @DisplayName("Should fill borders correctly in multi-row GUI")
    void shouldFillBorders() {
        CustomGui gui = new CustomGui("<blue>Border Test</blue>", 27);
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);

        gui.fillBorder(border);

        // Check top and bottom rows
        for (int col = 0; col < 9; col++) {
            assertThat(gui.getInventory().getItem(col)).isEqualTo(border);
            assertThat(gui.getInventory().getItem(18 + col)).isEqualTo(border);
        }

        // Check side columns for middle row
        assertThat(gui.getInventory().getItem(9)).isEqualTo(border);
        assertThat(gui.getInventory().getItem(17)).isEqualTo(border);

        // Inner slots should be empty
        for (int slot = 10; slot <= 16; slot++) {
            assertThat(gui.getInventory().getItem(slot)).isNull();
        }
    }

    @Test
    @DisplayName("Should handle pagination states and page navigation callbacks")
    void shouldHandlePagination() {
        CustomGui gui = new CustomGui("<aqua>Pagination Test</aqua>", 27);
        AtomicInteger pageChanges = new AtomicInteger(0);
        gui.setOnPageChange(pageChanges::set);

        assertThat(gui.getPage()).isEqualTo(1);
        assertThat(gui.getMaxPages()).isEqualTo(1);
        assertThat(gui.hasPreviousPage()).isFalse();
        assertThat(gui.hasNextPage()).isFalse();

        gui.setMaxPages(3);
        assertThat(gui.getMaxPages()).isEqualTo(3);
        assertThat(gui.hasNextPage()).isTrue();

        gui.nextPage();
        assertThat(gui.getPage()).isEqualTo(2);
        assertThat(pageChanges.get()).isEqualTo(2);
        assertThat(gui.hasPreviousPage()).isTrue();
        assertThat(gui.hasNextPage()).isTrue();

        gui.nextPage();
        assertThat(gui.getPage()).isEqualTo(3);
        assertThat(gui.hasNextPage()).isFalse();

        // Calling next on last page does nothing
        gui.nextPage();
        assertThat(gui.getPage()).isEqualTo(3);

        gui.previousPage();
        assertThat(gui.getPage()).isEqualTo(2);
        assertThat(pageChanges.get()).isEqualTo(2);

        gui.previousPage();
        assertThat(gui.getPage()).isEqualTo(1);
        assertThat(gui.hasPreviousPage()).isFalse();

        // Test pagination buttons
        ItemStack prevBtn = new ItemStack(Material.ARROW);
        ItemStack nextBtn = new ItemStack(Material.SPECTRAL_ARROW);
        gui.setPaginationButtons(18, prevBtn, 26, nextBtn);

        assertThat(gui.getItem(18)).isNotNull();
        assertThat(gui.getItem(26)).isNotNull();
    }

    @Test
    @DisplayName("Should invoke onClose callback when closed")
    void shouldTriggerOnCloseCallback() {
        CustomGui gui = new CustomGui("<purple>Close Test</purple>", 27);
        PlayerMock player = server.addPlayer("Steve");
        AtomicBoolean closed = new AtomicBoolean(false);

        gui.setOnClose(p -> {
            assertThat(p).isEqualTo(player);
            closed.set(true);
        });

        gui.onClose(player);
        assertThat(closed.get()).isTrue();
    }

    @Test
    @DisplayName("Should create components and items with explicit non-italic decoration")
    void shouldCreateNonItalicComponentsAndItems() {
        Component noItalic = CustomGui.textWithoutItalic("<green>Test Item</green>");
        assertThat(noItalic.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC))
            .isEqualTo(net.kyori.adventure.text.format.TextDecoration.State.FALSE);

        ItemStack item = CustomGui.createItem(
            Material.DIAMOND,
            "<gold>Item Name</gold>",
            List.of("<gray>Lore line 1</gray>", "<yellow>Lore line 2</yellow>")
        );

        assertThat(item.getItemMeta()).isNotNull();
        assertThat(item.getItemMeta().displayName().decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC))
            .isEqualTo(net.kyori.adventure.text.format.TextDecoration.State.FALSE);

        List<Component> lore = item.getItemMeta().lore();
        assertThat(lore).hasSize(2);
        assertThat(lore.get(0).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC))
            .isEqualTo(net.kyori.adventure.text.format.TextDecoration.State.FALSE);
        assertThat(lore.get(1).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC))
            .isEqualTo(net.kyori.adventure.text.format.TextDecoration.State.FALSE);
    }
}
