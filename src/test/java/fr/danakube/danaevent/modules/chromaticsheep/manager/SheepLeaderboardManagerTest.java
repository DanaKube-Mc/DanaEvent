package fr.danakube.danaevent.modules.chromaticsheep.manager;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.database.DatabaseConfig;
import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import fr.danakube.danaevent.modules.chromaticsheep.database.ChromaticSheepDatabase;
import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class SheepLeaderboardManagerTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private DanaEventPlugin plugin;
    private DatabaseManager databaseManager;
    private ChromaticSheepDatabase database;
    private SheepLeaderboardManager leaderboardManager;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);

        DatabaseConfig config = new DatabaseConfig(
            StorageType.SQLITE, "localhost", 3306, "test_slm.db", "", "", 5
        );
        databaseManager = new DatabaseManager(config, tempDir.toFile());
        database = new ChromaticSheepDatabase(databaseManager, Runnable::run);
        database.initTables();

        leaderboardManager = new SheepLeaderboardManager(plugin, database);
    }

    @AfterEach
    void tearDown() {
        if (leaderboardManager != null) {
            leaderboardManager.cleanUp();
        }
        if (databaseManager != null && databaseManager.isRunning()) {
            databaseManager.shutdown();
        }
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should retrieve top all-time and monthly records sorted by points descending")
    void shouldRetrieveTopRecords() throws ExecutionException, InterruptedException {
        String arenaId = "pasture";
        String currentMonth = leaderboardManager.getCurrentPeriodMonth();

        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        database.saveRecord(new SheepRecord(arenaId, u1, false, 25, 10, ScoringMode.ACTION_SCORE, currentMonth, Instant.now())).get();
        database.saveRecord(new SheepRecord(arenaId, u2, false, 60, 24, ScoringMode.ACTION_SCORE, currentMonth, Instant.now())).get();

        List<SheepRecord> topAllTime = leaderboardManager.getTopAllTime(arenaId, 10).get();
        assertThat(topAllTime).hasSize(2);
        assertThat(topAllTime.get(0).holderUuid()).isEqualTo(u2);
        assertThat(topAllTime.get(0).scorePoints()).isEqualTo(60);

        List<SheepRecord> topMonthly = leaderboardManager.getTopMonthly(arenaId, 10).get();
        assertThat(topMonthly).hasSize(2);
        assertThat(topMonthly.get(0).holderUuid()).isEqualTo(u2);

        Optional<SheepRecord> pb = leaderboardManager.getPersonalBest(arenaId, u1).get();
        assertThat(pb).isPresent();
        assertThat(pb.get().scorePoints()).isEqualTo(25);
    }

    @Test
    @DisplayName("Should resolve holder name for solo player and for team")
    void shouldResolveHolderNames() throws ExecutionException, InterruptedException {
        PlayerMock player = server.addPlayer("SheepHero");
        String playerName = leaderboardManager.resolveHolderName(player.getUniqueId(), false);
        assertThat(playerName).isEqualTo("SheepHero");

        // Team resolution
        DanaTeam team = plugin.getTeamManager().createTeam("alpha", "Alpha Squad", TeamColor.RED, player).get();

        UUID teamUuid = SheepLeaderboardManager.getTeamUuid("alpha");
        String teamName = leaderboardManager.resolveHolderName(teamUuid, true);
        assertThat(teamName).isEqualTo("Alpha Squad");

        String unknown = leaderboardManager.resolveHolderName(UUID.randomUUID(), false);
        assertThat(unknown).isEqualTo("Inconnu");

        String unknownTeam = leaderboardManager.resolveHolderName(UUID.randomUUID(), true);
        assertThat(unknownTeam).isEqualTo("Équipe Inconnue");
    }

    @Test
    @DisplayName("Should reset ranking and invalidate local cache")
    void shouldResetRankingAndInvalidate() throws ExecutionException, InterruptedException {
        String arenaId = "plains";
        String month = leaderboardManager.getCurrentPeriodMonth();
        UUID u1 = UUID.randomUUID();

        database.saveRecord(new SheepRecord(arenaId, u1, false, 40, 15, ScoringMode.FINAL_COUNT, month, Instant.now())).get();
        assertThat(leaderboardManager.getTopAllTime(arenaId, 5).get()).hasSize(1);

        int deleted = leaderboardManager.resetRanking(arenaId, null).get();
        assertThat(deleted).isEqualTo(1);

        assertThat(leaderboardManager.getTopAllTime(arenaId, 5).get()).isEmpty();
    }
}
