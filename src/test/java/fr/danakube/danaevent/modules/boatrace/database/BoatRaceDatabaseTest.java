package fr.danakube.danaevent.modules.boatrace.database;

import fr.danakube.danaevent.core.database.DatabaseConfig;
import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.modules.boatrace.model.RecordEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class BoatRaceDatabaseTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private BoatRaceDatabase boatRaceDatabase;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseConfig config = new DatabaseConfig(
            StorageType.SQLITE,
            "localhost",
            3306,
            "boatrace_test.db",
            "",
            "",
            5
        );
        databaseManager = new DatabaseManager(config, tempDir.toFile());
        boatRaceDatabase = new BoatRaceDatabase(databaseManager);
        boatRaceDatabase.initTables();
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
        boatRaceDatabase.initTables();
        boatRaceDatabase.initTables();
    }

    @Test
    @DisplayName("Should insert records asynchronously and query top records ordered by time ascending")
    void shouldInsertAndRetrieveTopRecordsOrdered() throws ExecutionException, InterruptedException {
        String trackId = "alpine_pass";
        String period = "2026-09";

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();
        UUID pOther = UUID.randomUUID();

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        RecordEntry slow = new RecordEntry(trackId, p1, 85000L, 3, period, now);
        RecordEntry fast = new RecordEntry(trackId, p2, 62000L, 3, period, now);
        RecordEntry medium = new RecordEntry(trackId, p3, 71000L, 3, period, now);

        // Different track / period to verify filtering
        RecordEntry otherTrack = new RecordEntry("other_track", pOther, 50000L, 3, period, now);
        RecordEntry otherPeriod = new RecordEntry(trackId, pOther, 51000L, 3, "2026-10", now);

        boatRaceDatabase.insertRecord(slow).get();
        boatRaceDatabase.insertRecord(fast).get();
        boatRaceDatabase.insertRecord(medium).get();
        boatRaceDatabase.insertRecord(otherTrack).get();
        boatRaceDatabase.insertRecord(otherPeriod).get();

        // Retrieve top 10 via getTopMonthly
        List<RecordEntry> top = boatRaceDatabase.getTopMonthly(trackId, period, 10).get();

        assertThat(top).hasSize(3);
        // Verify ascending ordering
        assertThat(top.get(0).playerUuid()).isEqualTo(p2);
        assertThat(top.get(0).timeMillis()).isEqualTo(62000L);
        assertThat(top.get(0).formatTime()).isEqualTo("01:02.000");

        assertThat(top.get(1).playerUuid()).isEqualTo(p3);
        assertThat(top.get(1).timeMillis()).isEqualTo(71000L);

        assertThat(top.get(2).playerUuid()).isEqualTo(p1);
        assertThat(top.get(2).timeMillis()).isEqualTo(85000L);

        // Verify limit works
        List<RecordEntry> top2 = boatRaceDatabase.getTopRecords(trackId, period, 2).get();
        assertThat(top2).hasSize(2);
        assertThat(top2.get(0).playerUuid()).isEqualTo(p2);
        assertThat(top2.get(1).playerUuid()).isEqualTo(p3);

        // Query non-existent track
        List<RecordEntry> empty = boatRaceDatabase.getTopRecords("non_existent", period, 10).get();
        assertThat(empty).isEmpty();
    }

    @Test
    @DisplayName("Should query getTopAllTime across all periods ordered ascending")
    void shouldRetrieveTopAllTimeAcrossPeriods() throws ExecutionException, InterruptedException {
        String trackId = "canyon_drift";
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        boatRaceDatabase.insertRecord(new RecordEntry(trackId, player1, 90000L, 3, "2026-08", now)).get();
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, player2, 75000L, 3, "2026-09", now)).get();
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, player1, 60000L, 3, "2026-10", now)).get();
        boatRaceDatabase.insertRecord(new RecordEntry("other_track", player2, 40000L, 3, "2026-09", now)).get();

        List<RecordEntry> allTime = boatRaceDatabase.getTopAllTime(trackId, 10).get();
        assertThat(allTime).hasSize(3);
        assertThat(allTime.get(0).timeMillis()).isEqualTo(60000L);
        assertThat(allTime.get(1).timeMillis()).isEqualTo(75000L);
        assertThat(allTime.get(2).timeMillis()).isEqualTo(90000L);
    }

    @Test
    @DisplayName("Should retrieve personal best and monthly personal best for player")
    void shouldRetrievePersonalBestAndMonthlyPB() throws ExecutionException, InterruptedException {
        String trackId = "neon_speedway";
        UUID player = UUID.randomUUID();
        UUID otherPlayer = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // Before inserting records
        assertThat(boatRaceDatabase.getPersonalBest(trackId, player).get()).isEmpty();
        assertThat(boatRaceDatabase.getMonthlyPersonalBest(trackId, player, "2026-09").get()).isEmpty();

        boatRaceDatabase.insertRecord(new RecordEntry(trackId, player, 80000L, 3, "2026-08", now)).get();
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, player, 95000L, 3, "2026-08", now)).get();
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, player, 65000L, 3, "2026-09", now)).get();
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, otherPlayer, 50000L, 3, "2026-09", now)).get();

        // All-time PB for player should be 65000ms
        var pbOpt = boatRaceDatabase.getPersonalBest(trackId, player).get();
        assertThat(pbOpt).isPresent();
        assertThat(pbOpt.get().timeMillis()).isEqualTo(65000L);
        assertThat(pbOpt.get().periodMonth()).isEqualTo("2026-09");

        // Monthly PB for 2026-08 should be 80000ms
        var monthlyAug = boatRaceDatabase.getMonthlyPersonalBest(trackId, player, "2026-08").get();
        assertThat(monthlyAug).isPresent();
        assertThat(monthlyAug.get().timeMillis()).isEqualTo(80000L);

        // Monthly PB for 2026-09 should be 65000ms
        var monthlySep = boatRaceDatabase.getMonthlyPersonalBest(trackId, player, "2026-09").get();
        assertThat(monthlySep).isPresent();
        assertThat(monthlySep.get().timeMillis()).isEqualTo(65000L);

        // Monthly PB for month without runs should be empty
        assertThat(boatRaceDatabase.getMonthlyPersonalBest(trackId, player, "2026-10").get()).isEmpty();
    }

    @Test
    @DisplayName("Should reset ranking for specific period or all periods")
    void shouldResetRanking() throws ExecutionException, InterruptedException {
        String trackId = "frozen_lake";
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        boatRaceDatabase.insertRecord(new RecordEntry(trackId, p1, 50000L, 2, "2026-09", now)).get();
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, p2, 55000L, 2, "2026-09", now)).get();
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, p1, 60000L, 2, "2026-10", now)).get();

        // Reset only 2026-09
        int deletedMonth = boatRaceDatabase.resetRanking(trackId, "2026-09").get();
        assertThat(deletedMonth).isEqualTo(2);

        assertThat(boatRaceDatabase.getTopMonthly(trackId, "2026-09", 10).get()).isEmpty();
        assertThat(boatRaceDatabase.getTopMonthly(trackId, "2026-10", 10).get()).hasSize(1);
        assertThat(boatRaceDatabase.getTopAllTime(trackId, 10).get()).hasSize(1);

        // Reset all
        int deletedAll = boatRaceDatabase.resetRanking(trackId, null).get();
        assertThat(deletedAll).isEqualTo(1);
        assertThat(boatRaceDatabase.getTopAllTime(trackId, 10).get()).isEmpty();
    }
}
