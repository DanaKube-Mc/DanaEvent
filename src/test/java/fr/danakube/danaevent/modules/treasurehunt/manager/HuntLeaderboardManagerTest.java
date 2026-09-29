package fr.danakube.danaevent.modules.treasurehunt.manager;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.database.DatabaseConfig;
import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.core.team.manager.TeamManager;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import fr.danakube.danaevent.modules.treasurehunt.database.TreasureHuntDatabase;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntRecord;
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

class HuntLeaderboardManagerTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private DanaEventPlugin plugin;
    private DatabaseManager databaseManager;
    private TreasureHuntDatabase database;
    private HuntLeaderboardManager leaderboardManager;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);

        DatabaseConfig config = new DatabaseConfig(
            StorageType.SQLITE, "localhost", 3306, "test_hlm.db", "", "", 5
        );
        databaseManager = new DatabaseManager(config, tempDir.toFile());
        database = new TreasureHuntDatabase(databaseManager, Runnable::run);
        database.initTables();

        leaderboardManager = new HuntLeaderboardManager(plugin, database);
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
    @DisplayName("Should retrieve top all-time and monthly records sorted by time ascending")
    void shouldRetrieveTopRecords() throws ExecutionException, InterruptedException {
        String huntId = "ruins";
        String currentMonth = leaderboardManager.getCurrentPeriodMonth();

        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        database.saveRecord(new HuntRecord(huntId, u1, false, 50_000L, currentMonth, Instant.now())).get();
        database.saveRecord(new HuntRecord(huntId, u2, false, 35_000L, currentMonth, Instant.now())).get();

        List<HuntRecord> topAllTime = leaderboardManager.getTopAllTime(huntId, 10).get();
        assertThat(topAllTime).hasSize(2);
        assertThat(topAllTime.get(0).holderUuid()).isEqualTo(u2);
        assertThat(topAllTime.get(0).timeMillis()).isEqualTo(35_000L);

        List<HuntRecord> topMonthly = leaderboardManager.getTopMonthly(huntId, 10).get();
        assertThat(topMonthly).hasSize(2);
        assertThat(topMonthly.get(0).holderUuid()).isEqualTo(u2);

        Optional<HuntRecord> pb = leaderboardManager.getPersonalBest(huntId, u1).get();
        assertThat(pb).isPresent();
        assertThat(pb.get().timeMillis()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("Should resolve holder name for solo player and for team")
    void shouldResolveHolderNames() throws ExecutionException, InterruptedException {
        PlayerMock player = server.addPlayer("SoloHero");
        String playerName = leaderboardManager.resolveHolderName(player.getUniqueId(), false);
        assertThat(playerName).isEqualTo("SoloHero");

        TeamManager teamManager = plugin.getTeamManager();
        DanaTeam team = teamManager.createTeam("legion", "La Légion", TeamColor.YELLOW, player).get();
        UUID teamUuid = HuntProgressManager.getTeamUuid(team.getId());

        String resolvedTeamName = leaderboardManager.resolveHolderName(teamUuid, true);
        assertThat(resolvedTeamName).isEqualTo("La Légion");
    }

    @Test
    @DisplayName("Should reset ranking and invalidate cache")
    void shouldResetRanking() throws ExecutionException, InterruptedException {
        String huntId = "atlantis";
        UUID u1 = UUID.randomUUID();
        database.saveRecord(new HuntRecord(huntId, u1, false, 40_000L, "2026-09", Instant.now())).get();

        int deleted = leaderboardManager.resetRanking(huntId, null).get();
        assertThat(deleted).isEqualTo(1);

        List<HuntRecord> after = leaderboardManager.getTopAllTime(huntId, 10).get();
        assertThat(after).isEmpty();
    }
}
