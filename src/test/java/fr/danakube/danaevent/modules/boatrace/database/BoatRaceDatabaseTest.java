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

        // Retrieve top 10
        List<RecordEntry> top = boatRaceDatabase.getTopRecords(trackId, period, 10).get();

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
}
