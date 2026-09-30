package fr.danakube.danaevent.modules.chromaticsheep;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.hook.DanaEventPlaceholderExpansion;
import fr.danakube.danaevent.modules.chromaticsheep.database.ChromaticSheepDatabase;
import fr.danakube.danaevent.modules.chromaticsheep.manager.SheepLeaderboardManager;
import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class ChromaticSheepPlaceholderTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private ChromaticSheepModule module;
    private ChromaticSheepDatabase database;
    private SheepLeaderboardManager leaderboardManager;
    private DanaEventPlaceholderExpansion expansion;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        module = plugin.getChromaticSheepModule();
        database = module.getDatabase();
        leaderboardManager = module.getLeaderboardManager();
        expansion = new DanaEventPlaceholderExpansion(plugin);
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should return 'N/A' for all ChromaticSheep placeholders when no records exist")
    void shouldReturnDefaultFallbackWhenNoRecordsExist() {
        PlayerMock player = server.addPlayer("Alice");
        String arena = "farm";

        assertThat(expansion.onRequest(player, "mc_" + arena + "_pb")).isEqualTo("N/A");
        assertThat(expansion.onRequest(player, "mc_" + arena + "_top1_name")).isEqualTo("N/A");
        assertThat(expansion.onRequest(player, "mc_" + arena + "_top1_score")).isEqualTo("N/A");
        assertThat(expansion.onRequest(player, "mc_" + arena + "_monthly_top1_name")).isEqualTo("N/A");
        assertThat(expansion.onRequest(player, "mc_" + arena + "_monthly_top1_score")).isEqualTo("N/A");
    }

    @Test
    @DisplayName("Should resolve ChromaticSheep placeholders correctly when records are registered")
    void shouldResolvePlaceholdersWithRecords() throws ExecutionException, InterruptedException {
        String arena = "pasture";
        PlayerMock playerA = server.addPlayer("WoolMaster");
        PlayerMock playerB = server.addPlayer("ColorKing");

        String currentMonth = leaderboardManager.getCurrentPeriodMonth();
        String pastMonth = "2026-05";

        // Player A holds monthly record: 85 pts, 25 sheep
        database.saveRecord(new SheepRecord(
            arena, playerA.getUniqueId(), false, 85, 25,
            ScoringMode.ACTION_SCORE, currentMonth, Instant.now()
        )).get();

        // Player B holds past all-time record: 120 pts, 38 sheep
        database.saveRecord(new SheepRecord(
            arena, playerB.getUniqueId(), false, 120, 38,
            ScoringMode.ACTION_SCORE, pastMonth, Instant.now()
        )).get();

        // Warm up cache
        leaderboardManager.getTopMonthly(arena, 5).get();
        leaderboardManager.getTopAllTime(arena, 5).get();
        leaderboardManager.getPersonalBest(arena, playerA.getUniqueId()).get();
        leaderboardManager.getPersonalBest(arena, playerB.getUniqueId()).get();

        // 1. Personal best
        assertThat(expansion.onRequest(playerA, "mc_" + arena + "_pb")).isEqualTo("85");
        assertThat(expansion.onRequest(playerB, "mc_" + arena + "_pb")).isEqualTo("120");

        // 2. Top 1 monthly name & score (spec default: %danaevent_mc_<arena>_top1_name%)
        assertThat(expansion.onRequest(playerA, "mc_" + arena + "_top1_name")).isEqualTo("WoolMaster");
        assertThat(expansion.onRequest(playerA, "mc_" + arena + "_top1_score")).isEqualTo("85");
        assertThat(expansion.onRequest(playerA, "mc_" + arena + "_top1_sheep")).isEqualTo("25");

        // 3. All-time top 1 name & score
        assertThat(expansion.onRequest(playerA, "mc_" + arena + "_alltime_top1_name")).isEqualTo("ColorKing");
        assertThat(expansion.onRequest(playerA, "mc_" + arena + "_alltime_top1_score")).isEqualTo("120");
        assertThat(expansion.onRequest(playerA, "mc_" + arena + "_alltime_top1_sheep")).isEqualTo("38");
    }
}
