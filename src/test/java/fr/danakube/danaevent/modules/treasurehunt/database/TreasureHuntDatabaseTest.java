package fr.danakube.danaevent.modules.treasurehunt.database;

import fr.danakube.danaevent.core.database.DatabaseConfig;
import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntRecord;
import fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class TreasureHuntDatabaseTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private TreasureHuntDatabase database;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseConfig config = new DatabaseConfig(
            StorageType.SQLITE,
            "localhost",
            3306,
            "treasurehunt_test.db",
            "",
            "",
            5
        );
        databaseManager = new DatabaseManager(config, tempDir.toFile());
        database = new TreasureHuntDatabase(databaseManager, Runnable::run);
        database.initTables();
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null && databaseManager.isRunning()) {
            databaseManager.shutdown();
        }
    }

    @Test
    @DisplayName("initTables should be idempotent")
    void shouldBeIdempotentOnTableInit() throws SQLException {
        database.initTables();
        database.initTables();
    }

    @Test
    @DisplayName("Should save, load, and delete active player hunt progress")
    void shouldSaveLoadAndDeleteProgress() throws ExecutionException, InterruptedException {
        UUID playerUuid = UUID.randomUUID();
        PlayerHuntProgress progress = PlayerHuntProgress.start(
            playerUuid,
            false,
            "jungle_ruins",
            List.of(1, 3, 2)
        );
        progress.advanceStep(); // at step index 1 (step number 3)

        database.saveProgress(progress).get();

        Optional<PlayerHuntProgress> loadedOpt = database.loadProgress(playerUuid).get();
        assertThat(loadedOpt).isPresent();
        PlayerHuntProgress loaded = loadedOpt.get();
        assertThat(loaded.getHolderUuid()).isEqualTo(playerUuid);
        assertThat(loaded.isTeam()).isFalse();
        assertThat(loaded.getHuntId()).isEqualTo("jungle_ruins");
        assertThat(loaded.getCurrentStepIndex()).isEqualTo(1);
        assertThat(loaded.getActiveStepNumber()).isEqualTo(3);
        assertThat(loaded.getStepOrder()).containsExactly(1, 3, 2);

        // Delete progress
        database.deleteProgress(playerUuid).get();
        assertThat(database.loadProgress(playerUuid).get()).isEmpty();
    }

    @Test
    @DisplayName("Should save records and return deduplicated top rankings sorted by time ascending")
    void shouldSaveAndQueryTopRankings() throws ExecutionException, InterruptedException {
        String huntId = "sunken_galleon";
        String period = "2026-09";

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        // P1 has 2 records: 60s and 45s (45s is their best)
        database.saveRecord(new HuntRecord(huntId, p1, false, 60000L, period, Instant.now())).get();
        database.saveRecord(new HuntRecord(huntId, p1, false, 45000L, period, Instant.now())).get();

        // P2 has 1 record: 35s (fastest)
        database.saveRecord(new HuntRecord(huntId, p2, false, 35000L, period, Instant.now())).get();

        // P3 has 1 record in different period
        database.saveRecord(new HuntRecord(huntId, p3, false, 25000L, "2026-08", Instant.now())).get();

        // Top for 2026-09
        List<HuntRecord> topMonthly = database.getTopRecords(huntId, period, 10).get();
        assertThat(topMonthly).hasSize(2);
        // Rank 1: P2 at 35s
        assertThat(topMonthly.get(0).holderUuid()).isEqualTo(p2);
        assertThat(topMonthly.get(0).timeMillis()).isEqualTo(35000L);
        // Rank 2: P1 at 45s (best time kept)
        assertThat(topMonthly.get(1).holderUuid()).isEqualTo(p1);
        assertThat(topMonthly.get(1).timeMillis()).isEqualTo(45000L);

        // Top All-Time
        List<HuntRecord> topAllTime = database.getTopRecords(huntId, "ALL_TIME", 10).get();
        assertThat(topAllTime).hasSize(3);
        assertThat(topAllTime.get(0).holderUuid()).isEqualTo(p3); // 25s
        assertThat(topAllTime.get(1).holderUuid()).isEqualTo(p2); // 35s
        assertThat(topAllTime.get(2).holderUuid()).isEqualTo(p1); // 45s
    }

    @Test
    @DisplayName("Should query personal best record for a player")
    void shouldRetrievePersonalBest() throws ExecutionException, InterruptedException {
        String huntId = "crypt_trials";
        UUID playerUuid = UUID.randomUUID();

        database.saveRecord(new HuntRecord(huntId, playerUuid, false, 90000L, "2026-09", Instant.now())).get();
        database.saveRecord(new HuntRecord(huntId, playerUuid, false, 75000L, "2026-09", Instant.now())).get();

        Optional<HuntRecord> pbOpt = database.getPersonalBest(huntId, playerUuid).get();
        assertThat(pbOpt).isPresent();
        assertThat(pbOpt.get().timeMillis()).isEqualTo(75000L);
    }

    @Test
    @DisplayName("Should reset ranking for a specific period or all periods")
    void shouldResetRanking() throws ExecutionException, InterruptedException {
        String huntId = "volcano_treasure";
        UUID p1 = UUID.randomUUID();

        database.saveRecord(new HuntRecord(huntId, p1, false, 50000L, "2026-09", Instant.now())).get();
        database.saveRecord(new HuntRecord(huntId, p1, false, 55000L, "2026-10", Instant.now())).get();

        // Reset only 2026-09
        int deleted = database.resetRanking(huntId, "2026-09").get();
        assertThat(deleted).isEqualTo(1);
        assertThat(database.getTopRecords(huntId, "2026-09", 10).get()).isEmpty();
        assertThat(database.getTopRecords(huntId, "2026-10", 10).get()).hasSize(1);

        // Reset ALL
        int deletedAll = database.resetRanking(huntId, null).get();
        assertThat(deletedAll).isEqualTo(1);
        assertThat(database.getTopRecords(huntId, "ALL_TIME", 10).get()).isEmpty();
    }
}
