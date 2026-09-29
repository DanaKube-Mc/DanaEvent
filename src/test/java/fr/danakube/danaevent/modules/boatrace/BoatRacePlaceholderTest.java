package fr.danakube.danaevent.modules.boatrace;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.hook.DanaEventPlaceholderExpansion;
import fr.danakube.danaevent.modules.boatrace.database.BoatRaceDatabase;
import fr.danakube.danaevent.modules.boatrace.manager.BoatRaceLeaderboardManager;
import fr.danakube.danaevent.modules.boatrace.model.RecordEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class BoatRacePlaceholderTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private BoatRaceDatabase boatRaceDatabase;
    private BoatRaceLeaderboardManager leaderboardManager;
    private DanaEventPlaceholderExpansion expansion;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        boatRaceDatabase = new BoatRaceDatabase(plugin.getDatabaseManager());
        boatRaceDatabase.initTables();
        leaderboardManager = new BoatRaceLeaderboardManager(plugin, boatRaceDatabase);
        plugin.setBoatRaceLeaderboardManager(leaderboardManager);
        expansion = new DanaEventPlaceholderExpansion(plugin);
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should return 'N/A' for all BoatRace placeholders when no records exist")
    void shouldReturnDefaultFallbackWhenNoRecordsExist() {
        PlayerMock player = server.addPlayer("Alice");
        String track = "glacier_canyon";

        assertThat(expansion.onRequest(player, "boatrace_" + track + "_pb")).isEqualTo("N/A");
        assertThat(expansion.onRequest(player, "boatrace_" + track + "_top1_name")).isEqualTo("N/A");
        assertThat(expansion.onRequest(player, "boatrace_" + track + "_top1_time")).isEqualTo("N/A");
        assertThat(expansion.onRequest(player, "boatrace_" + track + "_monthly_top1_name")).isEqualTo("N/A");
        assertThat(expansion.onRequest(player, "boatrace_" + track + "_monthly_top1_time")).isEqualTo("N/A");
    }

    @Test
    @DisplayName("Should resolve BoatRace placeholders correctly when records are registered")
    void shouldResolvePlaceholdersWithRecords() throws ExecutionException, InterruptedException {
        String track = "ice_circuit";
        PlayerMock playerA = server.addPlayer("SpeedyAlice");
        PlayerMock playerB = server.addPlayer("ProBob");

        leaderboardManager.registerPlayerName(playerA.getUniqueId(), "SpeedyAlice");
        leaderboardManager.registerPlayerName(playerB.getUniqueId(), "ProBob");

        String currentMonth = leaderboardManager.getCurrentPeriodMonth();
        String pastMonth = "2026-06";

        // Player A runs in current month: 65123ms -> "01:05.123"
        boatRaceDatabase.insertRecord(new RecordEntry(track, playerA.getUniqueId(), 65123L, 3, currentMonth, Instant.now())).get();

        // Player B holds historical all-time record from past month: 52000ms -> "00:52.000"
        boatRaceDatabase.insertRecord(new RecordEntry(track, playerB.getUniqueId(), 52000L, 3, pastMonth, Instant.now())).get();

        // Preload caches
        leaderboardManager.refreshCache(track).get();
        leaderboardManager.getPersonalBest(track, playerA.getUniqueId()).get();
        leaderboardManager.getPersonalBest(track, playerB.getUniqueId()).get();

        // 1. Personal Best for Player A
        assertThat(expansion.onRequest(playerA, "boatrace_" + track + "_pb")).isEqualTo("01:05.123");

        // 2. Personal Best for Player B
        assertThat(expansion.onRequest(playerB, "boatrace_" + track + "_pb")).isEqualTo("00:52.000");

        // 3. Top 1 All-Time Name (Player B)
        assertThat(expansion.onRequest(playerA, "boatrace_" + track + "_top1_name")).isEqualTo("ProBob");

        // 4. Top 1 All-Time Time (52000ms)
        assertThat(expansion.onRequest(playerA, "boatrace_" + track + "_top1_time")).isEqualTo("00:52.000");

        // 5. Monthly Top 1 Name (Player A)
        assertThat(expansion.onRequest(playerA, "boatrace_" + track + "_monthly_top1_name")).isEqualTo("SpeedyAlice");

        // 6. Monthly Top 1 Time (65123ms)
        assertThat(expansion.onRequest(playerA, "boatrace_" + track + "_monthly_top1_time")).isEqualTo("01:05.123");
    }

    @Test
    @DisplayName("Should handle tracks with underscores and non-existent track lookups")
    void shouldHandleComplexTrackNames() throws ExecutionException, InterruptedException {
        String track = "super_long_blue_ice_track_v2";
        UUID runner = UUID.randomUUID();
        leaderboardManager.registerPlayerName(runner, "IceMaster");

        leaderboardManager.recordTime(track, runner, 42100L, 1).get();

        assertThat(expansion.onRequest(null, "boatrace_" + track + "_top1_name")).isEqualTo("IceMaster");
        assertThat(expansion.onRequest(null, "boatrace_" + track + "_top1_time")).isEqualTo("00:42.100");

        // Unknown track
        assertThat(expansion.onRequest(null, "boatrace_unknown_track_top1_name")).isEqualTo("N/A");
        assertThat(expansion.onRequest(null, "boatrace_unknown_track_top1_time")).isEqualTo("N/A");
    }

    @Test
    @DisplayName("HookManager should transparently handle PAPI without crashing when PAPI is absent")
    void shouldFallbackTransparentlyInHookManager() {
        PlayerMock player = server.addPlayer("Charlie");
        String text = "Record: %danaevent_boatrace_test_pb%";

        // Since PAPI is a soft dependency and not installed in test runtime, original string is preserved
        String parsed = plugin.getHookManager().parsePlaceholders(player, text);
        assertThat(parsed).isEqualTo(text);
    }
}
