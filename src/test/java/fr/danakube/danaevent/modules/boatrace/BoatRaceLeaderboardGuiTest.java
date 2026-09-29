package fr.danakube.danaevent.modules.boatrace;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.modules.boatrace.database.BoatRaceDatabase;
import fr.danakube.danaevent.modules.boatrace.gui.BoatRaceLeaderboardGui;
import fr.danakube.danaevent.modules.boatrace.manager.BoatRaceLeaderboardManager;
import fr.danakube.danaevent.modules.boatrace.model.RecordEntry;
import fr.danakube.danaevent.modules.boatrace.model.Track;
import fr.danakube.danaevent.modules.boatrace.model.TrackMode;
import fr.danakube.danaevent.modules.boatrace.model.TrackType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class BoatRaceLeaderboardGuiTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private BoatRaceDatabase boatRaceDatabase;
    private BoatRaceLeaderboardManager leaderboardManager;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        boatRaceDatabase = new BoatRaceDatabase(plugin.getDatabaseManager());
        boatRaceDatabase.initTables();
        leaderboardManager = new BoatRaceLeaderboardManager(plugin, boatRaceDatabase);
        plugin.setBoatRaceLeaderboardManager(leaderboardManager);
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should create 54-slot GUI with gradient title and empty notice when no records")
    void shouldCreateGuiWithEmptyNotice() throws ExecutionException, InterruptedException {
        PlayerMock player = server.addPlayer("Alice");
        leaderboardManager.refreshCache("glacier_pass").get();
        BoatRaceLeaderboardGui gui = new BoatRaceLeaderboardGui(plugin, leaderboardManager, "glacier_pass");

        gui.open(player).join();

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(54);

        String plainTitle = PlainTextComponentSerializer.plainText().serialize(gui.getCustomGui().getTitle());
        assertThat(plainTitle).contains("Classement - glacier_pass");

        // Center slot should show empty notice
        ItemStack emptyItem = inv.getItem(BoatRaceLeaderboardGui.EMPTY_INFO_SLOT);
        assertThat(emptyItem).isNotNull();
        assertThat(emptyItem.getType()).isEqualTo(Material.SPYGLASS);
        String name = PlainTextComponentSerializer.plainText().serialize(emptyItem.getItemMeta().displayName());
        assertThat(name).contains("Aucun record");
    }

    @Test
    @DisplayName("Should render player heads with rank badges, formatted time, laps and date in central slots")
    void shouldRenderPlayerHeadsWithDetails() throws ExecutionException, InterruptedException {
        String trackId = "alpine_blizzard";
        PlayerMock racer1 = server.addPlayer("RacerOne");
        PlayerMock racer2 = server.addPlayer("RacerTwo");
        PlayerMock viewer = server.addPlayer("Spectator");

        leaderboardManager.registerPlayerName(racer1.getUniqueId(), "RacerOne");
        leaderboardManager.registerPlayerName(racer2.getUniqueId(), "RacerTwo");

        String currentMonth = leaderboardManager.getCurrentPeriodMonth();
        // Racer1 is fastest: 58000ms -> "00:58.000"
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, racer1.getUniqueId(), 58000L, 3, currentMonth, Instant.now())).get();
        // Racer2 is second: 65250ms -> "01:05.250"
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, racer2.getUniqueId(), 65250L, 3, currentMonth, Instant.now())).get();

        leaderboardManager.refreshCache(trackId).get();

        BoatRaceLeaderboardGui gui = new BoatRaceLeaderboardGui(plugin, leaderboardManager, trackId);
        gui.open(viewer).join();

        Inventory inv = viewer.getOpenInventory().getTopInventory();

        // Slot 10 should be Rank #1 (RacerOne)
        ItemStack head1 = inv.getItem(BoatRaceLeaderboardGui.CENTRAL_SLOTS[0]);
        assertThat(head1).isNotNull();
        assertThat(head1.getType()).isEqualTo(Material.PLAYER_HEAD);

        String name1 = PlainTextComponentSerializer.plainText().serialize(head1.getItemMeta().displayName());
        assertThat(name1).contains("#1").contains("RacerOne");

        List<String> lore1 = head1.getItemMeta().lore().stream()
            .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
            .toList();

        assertThat(lore1.get(0)).contains("Temps :").contains("00:58.000");
        assertThat(lore1.get(1)).contains("Tours :").contains("3 tours");
        assertThat(lore1.get(2)).contains("Date :");
        assertThat(lore1.get(3)).contains("Période :").contains(currentMonth);

        // Slot 11 should be Rank #2 (RacerTwo)
        ItemStack head2 = inv.getItem(BoatRaceLeaderboardGui.CENTRAL_SLOTS[1]);
        assertThat(head2).isNotNull();
        assertThat(head2.getType()).isEqualTo(Material.PLAYER_HEAD);

        String name2 = PlainTextComponentSerializer.plainText().serialize(head2.getItemMeta().displayName());
        assertThat(name2).contains("#2").contains("RacerTwo");

        List<String> lore2 = head2.getItemMeta().lore().stream()
            .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
            .toList();

        assertThat(lore2.get(0)).contains("Temps :").contains("01:05.250");
    }

    @Test
    @DisplayName("Should toggle between Monthly and All-Time scope upon clicking filter button")
    void shouldToggleScopeOnFilterButtonClick() throws ExecutionException, InterruptedException {
        String trackId = "coastal_rush";
        UUID monthlyWinner = UUID.randomUUID();
        UUID historicalWinner = UUID.randomUUID();

        leaderboardManager.registerPlayerName(monthlyWinner, "MonthlyHero");
        leaderboardManager.registerPlayerName(historicalWinner, "HistoricalLegend");

        String currentMonth = leaderboardManager.getCurrentPeriodMonth();
        String pastMonth = "2026-07";

        // Current month record
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, monthlyWinner, 70000L, 2, currentMonth, Instant.now())).get();
        // Past month historical record (faster)
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, historicalWinner, 49000L, 2, pastMonth, Instant.now())).get();

        leaderboardManager.refreshCache(trackId).get();

        PlayerMock player = server.addPlayer("Viewer");
        BoatRaceLeaderboardGui gui = new BoatRaceLeaderboardGui(plugin, leaderboardManager, trackId);
        gui.open(player).join();

        // By default scope is MONTHLY
        assertThat(gui.getScope()).isEqualTo(BoatRaceLeaderboardGui.Scope.MONTHLY);
        Inventory inv = player.getOpenInventory().getTopInventory();

        // Top 1 in Monthly should be MonthlyHero
        ItemStack rank1Monthly = inv.getItem(BoatRaceLeaderboardGui.CENTRAL_SLOTS[0]);
        String r1Name = PlainTextComponentSerializer.plainText().serialize(rank1Monthly.getItemMeta().displayName());
        assertThat(r1Name).contains("MonthlyHero");

        // Simulate click on filter toggle button (slot 48)
        InventoryClickEvent clickFilter = new InventoryClickEvent(
            player.getOpenInventory(),
            InventoryType.SlotType.CONTAINER,
            BoatRaceLeaderboardGui.FILTER_SLOT,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL
        );
        server.getPluginManager().callEvent(clickFilter);

        // Scope should now be ALL_TIME
        assertThat(gui.getScope()).isEqualTo(BoatRaceLeaderboardGui.Scope.ALL_TIME);

        // Under ALL_TIME, HistoricalLegend (49s) should be #1
        ItemStack rank1AllTime = inv.getItem(BoatRaceLeaderboardGui.CENTRAL_SLOTS[0]);
        String r1AllTimeName = PlainTextComponentSerializer.plainText().serialize(rank1AllTime.getItemMeta().displayName());
        assertThat(r1AllTimeName).contains("HistoricalLegend");

        // Click again to toggle back to MONTHLY
        server.getPluginManager().callEvent(clickFilter);
        assertThat(gui.getScope()).isEqualTo(BoatRaceLeaderboardGui.Scope.MONTHLY);
    }

    @Test
    @DisplayName("Should close inventory when clicking close button")
    void shouldCloseInventoryOnCloseButtonClick() throws ExecutionException, InterruptedException {
        PlayerMock player = server.addPlayer("Clicker");
        leaderboardManager.refreshCache("test_track").get();
        BoatRaceLeaderboardGui gui = new BoatRaceLeaderboardGui(plugin, leaderboardManager, "test_track");
        gui.open(player).join();

        assertThat(player.getOpenInventory().getTopInventory()).isSameAs(gui.getCustomGui().getInventory());

        // Simulate click on close button (slot 50)
        InventoryClickEvent clickClose = new InventoryClickEvent(
            player.getOpenInventory(),
            InventoryType.SlotType.CONTAINER,
            BoatRaceLeaderboardGui.CLOSE_SLOT,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL
        );
        server.getPluginManager().callEvent(clickClose);

        // Player's top inventory should no longer be the GUI
        assertThat(player.getOpenInventory().getTopInventory()).isNotSameAs(gui.getCustomGui().getInventory());
    }

    @Test
    @DisplayName("Should render only best time per player and suppress default italics across all GUI items and lore")
    void shouldRenderOnlyBestTimePerPlayerAndSuppressDefaultItalics() throws ExecutionException, InterruptedException {
        String trackId = "dedup_track";
        PlayerMock racer1 = server.addPlayer("MultiRacer1");
        PlayerMock racer2 = server.addPlayer("MultiRacer2");
        PlayerMock viewer = server.addPlayer("Viewer");

        leaderboardManager.registerPlayerName(racer1.getUniqueId(), "MultiRacer1");
        leaderboardManager.registerPlayerName(racer2.getUniqueId(), "MultiRacer2");

        String currentMonth = leaderboardManager.getCurrentPeriodMonth();

        // Racer1 has 3 runs in current month: 80s, 50s (PB), 70s
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, racer1.getUniqueId(), 80000L, 3, currentMonth, Instant.now())).get();
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, racer1.getUniqueId(), 50000L, 3, currentMonth, Instant.now())).get();
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, racer1.getUniqueId(), 70000L, 3, currentMonth, Instant.now())).get();

        // Racer2 has 2 runs in current month: 60s, 55s (PB)
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, racer2.getUniqueId(), 60000L, 3, currentMonth, Instant.now())).get();
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, racer2.getUniqueId(), 55000L, 3, currentMonth, Instant.now())).get();

        leaderboardManager.refreshCache(trackId).get();

        BoatRaceLeaderboardGui gui = new BoatRaceLeaderboardGui(plugin, leaderboardManager, trackId);
        gui.open(viewer).join();

        Inventory inv = viewer.getOpenInventory().getTopInventory();

        // Exactly 2 players should appear in central slots (slot 10: Racer1 at 50s, slot 11: Racer2 at 55s)
        ItemStack slot10 = inv.getItem(BoatRaceLeaderboardGui.CENTRAL_SLOTS[0]);
        ItemStack slot11 = inv.getItem(BoatRaceLeaderboardGui.CENTRAL_SLOTS[1]);
        ItemStack slot12 = inv.getItem(BoatRaceLeaderboardGui.CENTRAL_SLOTS[2]);

        assertThat(slot10).isNotNull();
        assertThat(slot10.getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(PlainTextComponentSerializer.plainText().serialize(slot10.getItemMeta().displayName()))
            .contains("#1").contains("MultiRacer1");

        assertThat(slot11).isNotNull();
        assertThat(slot11.getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(PlainTextComponentSerializer.plainText().serialize(slot11.getItemMeta().displayName()))
            .contains("#2").contains("MultiRacer2");

        // Third central slot MUST be empty because each player only has 1 record
        assertThat(slot12).isNull();

        // Verify non-italic styling on player head #1
        assertThat(slot10.getItemMeta().displayName().decoration(TextDecoration.ITALIC))
            .isEqualTo(TextDecoration.State.FALSE);
        for (Component line : slot10.getItemMeta().lore()) {
            assertThat(line.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
        }

        // Verify non-italic styling on filter button
        ItemStack filterItem = inv.getItem(BoatRaceLeaderboardGui.FILTER_SLOT);
        assertThat(filterItem).isNotNull();
        assertThat(filterItem.getItemMeta().displayName().decoration(TextDecoration.ITALIC))
            .isEqualTo(TextDecoration.State.FALSE);
        for (Component line : filterItem.getItemMeta().lore()) {
            assertThat(line.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
        }

        // Verify non-italic styling on close button
        ItemStack closeItem = inv.getItem(BoatRaceLeaderboardGui.CLOSE_SLOT);
        assertThat(closeItem).isNotNull();
        assertThat(closeItem.getItemMeta().displayName().decoration(TextDecoration.ITALIC))
            .isEqualTo(TextDecoration.State.FALSE);
        for (Component line : closeItem.getItemMeta().lore()) {
            assertThat(line.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
        }
    }

    @Test
    @DisplayName("Should render Sprint mode instead of laps when track is of SPRINT type")
    void shouldRenderSprintModeForSprintTrack() throws ExecutionException, InterruptedException {
        String trackId = "glacier_sprint";
        PlayerMock racer = server.addPlayer("SprintKing");
        PlayerMock viewer = server.addPlayer("SpectatorSprint");

        leaderboardManager.registerPlayerName(racer.getUniqueId(), "SprintKing");

        // Register SPRINT track in BoatRaceModule
        var brmOpt = plugin.getModuleManager().getModule("boatrace");
        if (brmOpt.isPresent() && brmOpt.get() instanceof BoatRaceModule brm) {
            Track sprintTrack = new Track(trackId, "Glacier Sprint", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
            brm.getTrackManager().registerTrack(sprintTrack);
        }

        String currentMonth = leaderboardManager.getCurrentPeriodMonth();
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, racer.getUniqueId(), 35000L, 1, currentMonth, Instant.now())).get();
        leaderboardManager.refreshCache(trackId).get();

        BoatRaceLeaderboardGui gui = new BoatRaceLeaderboardGui(plugin, leaderboardManager, trackId);
        gui.open(viewer).join();

        Inventory inv = viewer.getOpenInventory().getTopInventory();
        ItemStack head = inv.getItem(BoatRaceLeaderboardGui.CENTRAL_SLOTS[0]);
        assertThat(head).isNotNull();

        List<String> lore = head.getItemMeta().lore().stream()
            .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
            .toList();

        assertThat(lore).anyMatch(line -> line.contains("Mode :") && line.contains("Sprint"));
        assertThat(lore).noneMatch(line -> line.contains("Tours :"));
    }
}
