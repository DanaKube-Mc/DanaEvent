package fr.danakube.danaevent.modules.chromaticsheep.database;

import fr.danakube.danaevent.core.database.DatabaseConfig;
import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepRecord;
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

class ChromaticSheepDatabaseTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private ChromaticSheepDatabase database;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseConfig config = new DatabaseConfig(
            StorageType.SQLITE,
            "localhost",
            3306,
            "chromaticsheep_test.db",
            "",
            "",
            5
        );
        databaseManager = new DatabaseManager(config, tempDir.toFile());
        database = new ChromaticSheepDatabase(databaseManager, Runnable::run);
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
    @DisplayName("Should save records and return deduplicated top rankings sorted by score descending")
    void shouldSaveAndQueryTopRankings() throws ExecutionException, InterruptedException {
        String arenaId = "pasture_championship";
        String period = "2026-09";

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        // P1 has 2 records in 2026-09: 150 points (10 sheep) and 220 points (15 sheep)
        database.saveRecord(new SheepRecord(arenaId, p1, false, 150, 10, ScoringMode.FINAL_COUNT, period, Instant.now())).get();
        database.saveRecord(new SheepRecord(arenaId, p1, false, 220, 15, ScoringMode.FINAL_COUNT, period, Instant.now())).get();

        // P2 has 1 record: 300 points (18 sheep) - Highest in period
        database.saveRecord(new SheepRecord(arenaId, p2, false, 300, 18, ScoringMode.FINAL_COUNT, period, Instant.now())).get();

        // P3 has 1 record in another period: 400 points (25 sheep)
        database.saveRecord(new SheepRecord(arenaId, p3, false, 400, 25, ScoringMode.FINAL_COUNT, "2026-08", Instant.now())).get();

        // Query Monthly Top for 2026-09
        List<SheepRecord> monthly = database.getTopMonthly(arenaId, period, 10).get();
        assertThat(monthly).hasSize(2);

        // Rank 1: P2 with 300 points
        assertThat(monthly.get(0).holderUuid()).isEqualTo(p2);
        assertThat(monthly.get(0).scorePoints()).isEqualTo(300);
        assertThat(monthly.get(0).sheepCount()).isEqualTo(18);

        // Rank 2: P1 with 220 points (best record deduplicated)
        assertThat(monthly.get(1).holderUuid()).isEqualTo(p1);
        assertThat(monthly.get(1).scorePoints()).isEqualTo(220);
        assertThat(monthly.get(1).sheepCount()).isEqualTo(15);

        // Query All-Time Top
        List<SheepRecord> allTime = database.getTopAllTime(arenaId, 10).get();
        assertThat(allTime).hasSize(3);
        assertThat(allTime.get(0).holderUuid()).isEqualTo(p3); // 400 pts
        assertThat(allTime.get(1).holderUuid()).isEqualTo(p2); // 300 pts
        assertThat(allTime.get(2).holderUuid()).isEqualTo(p1); // 220 pts
    }

    @Test
    @DisplayName("Should break ties using sheep_count descending")
    void shouldBreakTiesWithSheepCount() throws ExecutionException, InterruptedException {
        String arenaId = "tie_arena";
        String period = "2026-09";

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        // Both players have 200 points, but P2 captured 15 sheep while P1 captured 12 sheep
        database.saveRecord(new SheepRecord(arenaId, p1, false, 200, 12, ScoringMode.FINAL_COUNT, period, Instant.now())).get();
        database.saveRecord(new SheepRecord(arenaId, p2, false, 200, 15, ScoringMode.FINAL_COUNT, period, Instant.now())).get();

        List<SheepRecord> top = database.getTopRecords(arenaId, period, 10).get();
        assertThat(top).hasSize(2);
        assertThat(top.get(0).holderUuid()).isEqualTo(p2);
        assertThat(top.get(0).sheepCount()).isEqualTo(15);
        assertThat(top.get(1).holderUuid()).isEqualTo(p1);
        assertThat(top.get(1).sheepCount()).isEqualTo(12);
    }

    @Test
    @DisplayName("Should query personal best record accurately")
    void shouldRetrievePersonalBest() throws ExecutionException, InterruptedException {
        String arenaId = "pb_arena";
        UUID playerUuid = UUID.randomUUID();

        database.saveRecord(new SheepRecord(arenaId, playerUuid, false, 100, 8, ScoringMode.FINAL_COUNT, "2026-08", Instant.now())).get();
        database.saveRecord(new SheepRecord(arenaId, playerUuid, false, 250, 16, ScoringMode.FINAL_COUNT, "2026-09", Instant.now())).get();
        database.saveRecord(new SheepRecord(arenaId, playerUuid, false, 180, 12, ScoringMode.FINAL_COUNT, "2026-09", Instant.now())).get();

        Optional<SheepRecord> pbOpt = database.getPersonalBest(arenaId, playerUuid).get();
        assertThat(pbOpt).isPresent();
        assertThat(pbOpt.get().scorePoints()).isEqualTo(250);
        assertThat(pbOpt.get().sheepCount()).isEqualTo(16);
    }

    @Test
    @DisplayName("Should reset ranking for a specific period or all periods")
    void shouldResetRanking() throws ExecutionException, InterruptedException {
        String arenaId = "reset_arena";
        UUID p1 = UUID.randomUUID();

        database.saveRecord(new SheepRecord(arenaId, p1, false, 100, 10, ScoringMode.FINAL_COUNT, "2026-09", Instant.now())).get();
        database.saveRecord(new SheepRecord(arenaId, p1, false, 200, 15, ScoringMode.FINAL_COUNT, "2026-10", Instant.now())).get();

        // Reset period 2026-09
        int deleted = database.resetRanking(arenaId, "2026-09").get();
        assertThat(deleted).isEqualTo(1);
        assertThat(database.getTopMonthly(arenaId, "2026-09", 10).get()).isEmpty();
        assertThat(database.getTopMonthly(arenaId, "2026-10", 10).get()).hasSize(1);

        // Reset All
        int deletedAll = database.resetRanking(arenaId, null).get();
        assertThat(deletedAll).isEqualTo(1);
        assertThat(database.getTopAllTime(arenaId, 10).get()).isEmpty();
    }
}
