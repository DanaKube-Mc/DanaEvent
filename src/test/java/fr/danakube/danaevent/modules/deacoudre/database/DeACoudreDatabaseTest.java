package fr.danakube.danaevent.modules.deacoudre.database;

import fr.danakube.danaevent.core.database.DatabaseConfig;
import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.modules.deacoudre.model.DacRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class DeACoudreDatabaseTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private DeACoudreDatabase database;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseConfig config = new DatabaseConfig(
            StorageType.SQLITE, "localhost", 3306, "test_dac.db", "", "", 5
        );
        databaseManager = new DatabaseManager(config, tempDir.toFile());
        database = new DeACoudreDatabase(databaseManager, Runnable::run);
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
    void testInitTablesIdempotence() throws Exception {
        database.initTables();
        database.initTables();
    }

    @Test
    @DisplayName("Should record match results, increment existing records and query monthly rankings")
    void testRecordAndRankings() throws ExecutionException, InterruptedException {
        String arenaId = "olympic";
        String month = "2026-09";
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        // Player 1 wins match, 3 jumps, 1 perfect
        database.recordMatchResult(arenaId, p1, false, true, 3, 1, month).get();

        // Player 2 loses match, 2 jumps, 0 perfect
        database.recordMatchResult(arenaId, p2, false, false, 2, 0, month).get();

        // Player 1 wins another match, 4 jumps, 2 perfects
        database.recordMatchResult(arenaId, p1, false, true, 4, 2, month).get();

        List<DacRecord> monthly = database.getTopMonthly(arenaId, month, 10).get();
        assertThat(monthly).hasSize(2);

        DacRecord top1 = monthly.get(0);
        assertThat(top1.holderUuid()).isEqualTo(p1);
        assertThat(top1.wins()).isEqualTo(2);
        assertThat(top1.successfulJumps()).isEqualTo(7);
        assertThat(top1.perfectDacs()).isEqualTo(3);

        DacRecord top2 = monthly.get(1);
        assertThat(top2.holderUuid()).isEqualTo(p2);
        assertThat(top2.wins()).isEqualTo(0);
        assertThat(top2.successfulJumps()).isEqualTo(2);

        // All time across periods
        String otherMonth = "2026-10";
        database.recordMatchResult(arenaId, p2, false, true, 5, 1, otherMonth).get();

        List<DacRecord> allTime = database.getTopAllTime(arenaId, 10).get();
        assertThat(allTime).hasSize(2);
        assertThat(allTime.get(0).holderUuid()).isEqualTo(p1);
        assertThat(allTime.get(0).wins()).isEqualTo(2);
        assertThat(allTime.get(1).holderUuid()).isEqualTo(p2);
        assertThat(allTime.get(1).wins()).isEqualTo(1);
        assertThat(allTime.get(1).successfulJumps()).isEqualTo(7);

        // Total player perfects across arenas
        int p1Perfects = database.getPlayerTotalPerfects(p1).get();
        assertThat(p1Perfects).isEqualTo(3);

        // Personal stats
        Optional<DacRecord> stats = database.getPersonalStats(arenaId, p1).get();
        assertThat(stats).isPresent();
        assertThat(stats.get().wins()).isEqualTo(2);
        assertThat(stats.get().perfectDacs()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should reset ranking for an arena")
    void testResetRanking() throws ExecutionException, InterruptedException {
        String arenaId = "lagoon";
        UUID p1 = UUID.randomUUID();
        database.recordMatchResult(arenaId, p1, false, true, 3, 1, "2026-09").get();

        int deleted = database.resetRanking(arenaId, null).get();
        assertThat(deleted).isEqualTo(1);

        assertThat(database.getTopAllTime(arenaId, 5).get()).isEmpty();
    }
}
