package fr.danakube.danaevent.modules.boatrace.gui;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.modules.boatrace.manager.BoatSkinManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BoatSelectionGuiTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private DanaEventPlugin plugin;
    private BoatSkinManager boatSkinManager;
    private File customBoatsFile;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);

        customBoatsFile = tempDir.resolve("boats.yml").toFile();
        String yaml = """
            boats:
              oak:
                material: OAK_BOAT
                name: "<green>Bateau en Chêne</green>"
                permission: ""
              spruce:
                material: SPRUCE_BOAT
                name: "<dark_green>Bateau en Sapin</dark_green>"
                permission: ""
              bamboo:
                material: BAMBOO_RAFT
                name: "<gold>Radeau en Bambou</gold>"
                permission: "danaevent.boat.vip"
            """;
        Files.writeString(customBoatsFile.toPath(), yaml);

        boatSkinManager = new BoatSkinManager(plugin, null, customBoatsFile);
        boatSkinManager.loadBoatsConfig();
    }

    @AfterEach
    void tearDown() {
        if (boatSkinManager != null) {
            boatSkinManager.cleanUp();
        }
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private List<String> drainMessages(PlayerMock player) {
        List<String> messages = new ArrayList<>();
        Component comp;
        while ((comp = player.nextComponentMessage()) != null) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(comp));
        }
        return messages;
    }

    @Test
    @DisplayName("Should build GUI with borders, close button and correct boat items")
    void shouldBuildGuiLayoutCorrectly() {
        PlayerMock player = server.addPlayer("Alice");
        boatSkinManager.setPreference(player.getUniqueId(), Material.OAK_BOAT);

        BoatSelectionGui gui = new BoatSelectionGui(plugin, boatSkinManager);
        gui.open(player);

        assertThat(player.getOpenInventory().getTopInventory()).isNotNull();
        assertThat(player.getOpenInventory().getTopInventory().getSize()).isEqualTo(36);

        // Border slot 0 should be gray glass pane
        ItemStack border = player.getOpenInventory().getTopInventory().getItem(0);
        assertThat(border).isNotNull();
        assertThat(border.getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);

        // Close button at slot 31
        ItemStack closeBtn = player.getOpenInventory().getTopInventory().getItem(31);
        assertThat(closeBtn).isNotNull();
        assertThat(closeBtn.getType()).isEqualTo(Material.BARRIER);

        // Slot 11 should be OAK_BOAT with current boat lore
        ItemStack oakItem = player.getOpenInventory().getTopInventory().getItem(11);
        assertThat(oakItem).isNotNull();
        assertThat(oakItem.getType()).isEqualTo(Material.OAK_BOAT);

        ItemMeta oakMeta = oakItem.getItemMeta();
        assertThat(oakMeta).isNotNull();
        assertThat(oakMeta.hasLore()).isTrue();
        String oakLore = oakMeta.lore().stream()
            .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
            .reduce("", (a, b) -> a + " " + b);
        assertThat(oakLore).contains("✔ Bateau Actuel");

        // Slot 12 should be SPRUCE_BOAT (available but not current)
        ItemStack spruceItem = player.getOpenInventory().getTopInventory().getItem(12);
        assertThat(spruceItem).isNotNull();
        assertThat(spruceItem.getType()).isEqualTo(Material.SPRUCE_BOAT);
        String spruceLore = spruceItem.getItemMeta().lore().stream()
            .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
            .reduce("", (a, b) -> a + " " + b);
        assertThat(spruceLore).contains("Cliquez pour choisir ce bateau");

        // Slot 13 should be BAMBOO_RAFT (locked without permission)
        ItemStack bambooItem = player.getOpenInventory().getTopInventory().getItem(13);
        assertThat(bambooItem).isNotNull();
        assertThat(bambooItem.getType()).isEqualTo(Material.BAMBOO_RAFT);
        String bambooLore = bambooItem.getItemMeta().lore().stream()
            .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
            .reduce("", (a, b) -> a + " " + b);
        assertThat(bambooLore).contains("🔒 Verrouillé");
    }

    @Test
    @DisplayName("Clicking available boat should update preference, send message, close inventory and fire callback")
    void shouldSelectBoatOnClickAndTriggerCallback() {
        PlayerMock player = server.addPlayer("Bob");
        AtomicReference<Material> callbackReceived = new AtomicReference<>();

        BoatSelectionGui gui = new BoatSelectionGui(plugin, boatSkinManager, callbackReceived::set);
        gui.open(player);

        // Click slot 12 (SPRUCE_BOAT)
        var clickEvent = new InventoryClickEvent(
            player.getOpenInventory(),
            org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
            12,
            org.bukkit.event.inventory.ClickType.LEFT,
            org.bukkit.event.inventory.InventoryAction.PICKUP_ALL
        );
        plugin.getServer().getPluginManager().callEvent(clickEvent);

        assertThat(boatSkinManager.hasPreference(player.getUniqueId())).isTrue();
        assertThat(boatSkinManager.getPreference(player.getUniqueId())).isEqualTo(Material.SPRUCE_BOAT);
        assertThat(callbackReceived.get()).isEqualTo(Material.SPRUCE_BOAT);

        List<String> messages = drainMessages(player);
        assertThat(messages).anyMatch(m -> m.contains("Bateau en Sapin") || m.contains("sélectionné"));
    }

    @Test
    @DisplayName("Clicking locked boat should deny selection and notify player")
    void shouldDenyLockedBoatSelection() {
        PlayerMock player = server.addPlayer("Charlie");
        AtomicReference<Material> callbackReceived = new AtomicReference<>();

        BoatSelectionGui gui = new BoatSelectionGui(plugin, boatSkinManager, callbackReceived::set);
        gui.open(player);

        // Click slot 13 (BAMBOO_RAFT, VIP required)
        var clickEvent = new InventoryClickEvent(
            player.getOpenInventory(),
            org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
            13,
            org.bukkit.event.inventory.ClickType.LEFT,
            org.bukkit.event.inventory.InventoryAction.PICKUP_ALL
        );
        plugin.getServer().getPluginManager().callEvent(clickEvent);

        assertThat(boatSkinManager.hasPreference(player.getUniqueId())).isFalse();
        assertThat(callbackReceived.get()).isNull();

        List<String> messages = drainMessages(player);
        assertThat(messages).anyMatch(m -> m.contains("verrouillé") || m.contains("permission"));
    }

    @Test
    @DisplayName("Player with permission can select restricted boat")
    void shouldAllowRestrictedBoatWithPermission() {
        PlayerMock player = server.addPlayer("VIP_Player");
        player.addAttachment(plugin, "danaevent.boat.vip", true);

        AtomicReference<Material> callbackReceived = new AtomicReference<>();
        BoatSelectionGui gui = new BoatSelectionGui(plugin, boatSkinManager, callbackReceived::set);
        gui.open(player);

        // Click slot 13 (BAMBOO_RAFT)
        var clickEvent = new InventoryClickEvent(
            player.getOpenInventory(),
            org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
            13,
            org.bukkit.event.inventory.ClickType.LEFT,
            org.bukkit.event.inventory.InventoryAction.PICKUP_ALL
        );
        plugin.getServer().getPluginManager().callEvent(clickEvent);

        assertThat(boatSkinManager.getPreference(player.getUniqueId())).isEqualTo(Material.BAMBOO_RAFT);
        assertThat(callbackReceived.get()).isEqualTo(Material.BAMBOO_RAFT);
    }

    @Test
    @DisplayName("Should suppress default italics on all GUI items, buttons, and lore lines")
    void shouldSuppressDefaultItalicsOnAllItemsAndLore() {
        PlayerMock player = server.addPlayer("NonItalicTester");
        boatSkinManager.setPreference(player.getUniqueId(), Material.OAK_BOAT);

        BoatSelectionGui gui = new BoatSelectionGui(plugin, boatSkinManager);
        gui.open(player);

        var inv = player.getOpenInventory().getTopInventory();

        // 1. Close button
        ItemStack closeBtn = inv.getItem(BoatSelectionGui.CLOSE_SLOT);
        assertThat(closeBtn).isNotNull();
        assertThat(closeBtn.getItemMeta().displayName().decoration(TextDecoration.ITALIC))
            .isEqualTo(TextDecoration.State.FALSE);
        for (Component line : closeBtn.getItemMeta().lore()) {
            assertThat(line.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
        }

        // 2. Active Boat Item (slot 11: OAK_BOAT)
        ItemStack oakBoat = inv.getItem(11);
        assertThat(oakBoat).isNotNull();
        assertThat(oakBoat.getItemMeta().displayName().decoration(TextDecoration.ITALIC))
            .isEqualTo(TextDecoration.State.FALSE);
        for (Component line : oakBoat.getItemMeta().lore()) {
            assertThat(line.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
        }

        // 3. Inactive Unlocked Boat Item (slot 12: SPRUCE_BOAT)
        ItemStack spruceBoat = inv.getItem(12);
        assertThat(spruceBoat).isNotNull();
        assertThat(spruceBoat.getItemMeta().displayName().decoration(TextDecoration.ITALIC))
            .isEqualTo(TextDecoration.State.FALSE);
        for (Component line : spruceBoat.getItemMeta().lore()) {
            assertThat(line.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
        }

        // 4. Locked Boat Item (slot 13: BAMBOO_RAFT)
        ItemStack bambooRaft = inv.getItem(13);
        assertThat(bambooRaft).isNotNull();
        assertThat(bambooRaft.getItemMeta().displayName().decoration(TextDecoration.ITALIC))
            .isEqualTo(TextDecoration.State.FALSE);
        for (Component line : bambooRaft.getItemMeta().lore()) {
            assertThat(line.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
        }
    }
}
