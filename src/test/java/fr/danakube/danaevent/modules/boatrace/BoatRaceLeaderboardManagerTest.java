package fr.danakube.danaevent.modules.boatrace;

import fr.danakube.danaevent.core.database.DatabaseConfig;
import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.modules.boatrace.database.BoatRaceDatabase;
import fr.danakube.danaevent.modules.boatrace.manager.BoatRaceLeaderboardManager;
import fr.danakube.danaevent.modules.boatrace.model.RecordEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class BoatRaceLeaderboardManagerTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private BoatRaceDatabase boatRaceDatabase;
    private BoatRaceLeaderboardManager leaderboardManager;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseConfig config = new DatabaseConfig(
            StorageType.SQLITE,
            "localhost",
            3306,
            "leaderboard_test.db",
            "",
            "",
            5
        );
        databaseManager = new DatabaseManager(config, tempDir.toFile());
        boatRaceDatabase = new BoatRaceDatabase(databaseManager);
        boatRaceDatabase.initTables();
        leaderboardManager = new BoatRaceLeaderboardManager(boatRaceDatabase);
    }

    @AfterEach
    void tearDown() {
        if (leaderboardManager != null) {
            leaderboardManager.cleanUp();
        }
        if (databaseManager != null && databaseManager.isRunning()) {
            databaseManager.shutdown();
        }
    }

    @Test
    @DisplayName("getCurrentPeriodMonth should return current month formatted as YYYY-MM")
    void shouldReturnCurrentPeriodMonth() {
        String currentMonth = leaderboardManager.getCurrentPeriodMonth();
        assertThat(currentMonth).matches("^\\d{4}-\\d{2}$");
        assertThat(currentMonth).isEqualTo(YearMonth.now().toString());
    }

    @Test
    @DisplayName("Should insert records across multiple tracks and months and extract sorted leaderboards")
    void shouldInsertAndExtractSortedLeaderboards() throws ExecutionException, InterruptedException {
        String trackA = "circuit_glacier";
        String trackB = "canyon_rally";
        String currentMonth = leaderboardManager.getCurrentPeriodMonth();
        String previousMonth = "2026-08";

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();
        UUID p4 = UUID.randomUUID();

        // Track A - Current Month
        boatRaceDatabase.insertRecord(new RecordEntry(trackA, p1, 75000L, 3, currentMonth, Instant.now())).get();
        boatRaceDatabase.insertRecord(new RecordEntry(trackA, p2, 62000L, 3, currentMonth, Instant.now())).get();
        boatRaceDatabase.insertRecord(new RecordEntry(trackA, p3, 90000L, 3, currentMonth, Instant.now())).get();

        // Track A - Previous Month
        boatRaceDatabase.insertRecord(new RecordEntry(trackA, p4, 55000L, 3, previousMonth, Instant.now())).get();

        // Track B - Current Month
        boatRaceDatabase.insertRecord(new RecordEntry(trackB, p1, 45000L, 1, currentMonth, Instant.now())).get();

        // 1. Verify Monthly Top for Track A (Current Month)
        List<RecordEntry> monthlyTopA = leaderboardManager.getTopMonthly(trackA, 10).get();
        assertThat(monthlyTopA).hasSize(3);
        // Sorted ascending: p2 (62s) < p1 (75s) < p3 (90s)
        assertThat(monthlyTopA.get(0).playerUuid()).isEqualTo(p2);
        assertThat(monthlyTopA.get(0).timeMillis()).isEqualTo(62000L);
        assertThat(monthlyTopA.get(0).formatTime()).isEqualTo("01:02.000");

        assertThat(monthlyTopA.get(1).playerUuid()).isEqualTo(p1);
        assertThat(monthlyTopA.get(1).timeMillis()).isEqualTo(75000L);

        assertThat(monthlyTopA.get(2).playerUuid()).isEqualTo(p3);
        assertThat(monthlyTopA.get(2).timeMillis()).isEqualTo(90000L);

        // 2. Verify Monthly Top for Track A (Previous Month)
        List<RecordEntry> prevMonthlyTopA = leaderboardManager.getTopMonthly(trackA, previousMonth, 10).get();
        assertThat(prevMonthlyTopA).hasSize(1);
        assertThat(prevMonthlyTopA.get(0).playerUuid()).isEqualTo(p4);
        assertThat(prevMonthlyTopA.get(0).timeMillis()).isEqualTo(55000L);

        // 3. Verify All-Time Top for Track A (Across both months)
        List<RecordEntry> allTimeTopA = leaderboardManager.getTopAllTime(trackA, 10).get();
        assertThat(allTimeTopA).hasSize(4);
        // Sorted ascending: p4 (55s) < p2 (62s) < p1 (75s) < p3 (90s)
        assertThat(allTimeTopA.get(0).playerUuid()).isEqualTo(p4);
        assertThat(allTimeTopA.get(0).timeMillis()).isEqualTo(55000L);
        assertThat(allTimeTopA.get(1).playerUuid()).isEqualTo(p2);
        assertThat(allTimeTopA.get(1).timeMillis()).isEqualTo(62000L);
        assertThat(allTimeTopA.get(2).playerUuid()).isEqualTo(p1);
        assertThat(allTimeTopA.get(3).playerUuid()).isEqualTo(p3);

        // 4. Verify Track B does not bleed into Track A
        List<RecordEntry> monthlyTopB = leaderboardManager.getTopMonthly(trackB, 10).get();
        assertThat(monthlyTopB).hasSize(1);
        assertThat(monthlyTopB.get(0).playerUuid()).isEqualTo(p1);
        assertThat(monthlyTopB.get(0).timeMillis()).isEqualTo(45000L);
    }

    @Test
    @DisplayName("Should correctly calculate all-time and monthly personal bests for a player")
    void shouldCalculatePersonalBests() throws ExecutionException, InterruptedException {
        String trackId = "alpine_ice";
        UUID player = UUID.randomUUID();
        String month1 = "2026-07";
        String month2 = "2026-08";

        // Initial PB checks when no records exist
        assertThat(leaderboardManager.getPersonalBest(trackId, player).get()).isEmpty();
        assertThat(leaderboardManager.getMonthlyPersonalBest(trackId, player, month1).get()).isEmpty();

        // Insert runs for the player in month 1: 85s then improved to 72s
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, player, 85000L, 3, month1, Instant.now())).get();
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, player, 72000L, 3, month1, Instant.now())).get();

        // Insert runs in month 2: 64s
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, player, 64000L, 3, month2, Instant.now())).get();

        // Invalidate cache to test fresh computation
        leaderboardManager.invalidateCache(trackId);

        // All-Time PB should be 64000L
        Optional<RecordEntry> allTimePb = leaderboardManager.getPersonalBest(trackId, player).get();
        assertThat(allTimePb).isPresent();
        assertThat(allTimePb.get().timeMillis()).isEqualTo(64000L);
        assertThat(allTimePb.get().periodMonth()).isEqualTo(month2);

        // Monthly PB for month 1 should be 72000L
        Optional<RecordEntry> month1Pb = leaderboardManager.getMonthlyPersonalBest(trackId, player, month1).get();
        assertThat(month1Pb).isPresent();
        assertThat(month1Pb.get().timeMillis()).isEqualTo(72000L);

        // Monthly PB for month 2 should be 64000L
        Optional<RecordEntry> month2Pb = leaderboardManager.getMonthlyPersonalBest(trackId, player, month2).get();
        assertThat(month2Pb).isPresent();
        assertThat(month2Pb.get().timeMillis()).isEqualTo(64000L);

        // Non-existent month
        Optional<RecordEntry> month3Pb = leaderboardManager.getMonthlyPersonalBest(trackId, player, "2026-09").get();
        assertThat(month3Pb).isEmpty();
    }

    @Test
    @DisplayName("Should utilize in-memory cache and support invalidation")
    void shouldCacheAndInvalidateRecords() throws ExecutionException, InterruptedException {
        String trackId = "glacier_run";
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        String currentMonth = leaderboardManager.getCurrentPeriodMonth();

        boatRaceDatabase.insertRecord(new RecordEntry(trackId, player1, 80000L, 2, currentMonth, Instant.now())).get();

        // First call populates cache
        List<RecordEntry> top1 = leaderboardManager.getTopMonthly(trackId, 5).get();
        assertThat(top1).hasSize(1);
        assertThat(top1.get(0).playerUuid()).isEqualTo(player1);

        // Insert second record via raw database
        boatRaceDatabase.insertRecord(new RecordEntry(trackId, player2, 50000L, 2, currentMonth, Instant.now())).get();

        // Cached call still returns cached size 1
        List<RecordEntry> cachedTop = leaderboardManager.getTopMonthly(trackId, 5).get();
        assertThat(cachedTop).hasSize(1);

        // Synchronous cached top 1 monthly
        assertThat(leaderboardManager.getCachedTop1Monthly(trackId)).isPresent();
        assertThat(leaderboardManager.getCachedTop1Monthly(trackId).get().playerUuid()).isEqualTo(player1);

        // Invalidate cache
        leaderboardManager.invalidateCache(trackId);

        // Next call fetches fresh from database
        List<RecordEntry> freshTop = leaderboardManager.getTopMonthly(trackId, 5).get();
        assertThat(freshTop).hasSize(2);
        assertThat(freshTop.get(0).playerUuid()).isEqualTo(player2);
        assertThat(freshTop.get(0).timeMillis()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("recordTime should persist to DB and update caches automatically")
    void shouldRecordTimeAndUpdateCaches() throws ExecutionException, InterruptedException {
        String trackId = "turbo_circuit";
        UUID player = UUID.randomUUID();

        leaderboardManager.recordTime(trackId, player, 48000L, 3).get();

        // Immediately available in cache
        assertThat(leaderboardManager.getCachedTop1Monthly(trackId)).isPresent();
        assertThat(leaderboardManager.getCachedTop1Monthly(trackId).get().timeMillis()).isEqualTo(48000L);

        assertThat(leaderboardManager.getCachedTop1AllTime(trackId)).isPresent();
        assertThat(leaderboardManager.getCachedTop1AllTime(trackId).get().timeMillis()).isEqualTo(48000L);

        assertThat(leaderboardManager.getCachedPersonalBest(trackId, player)).isPresent();
        assertThat(leaderboardManager.getCachedPersonalBest(trackId, player).get().timeMillis()).isEqualTo(48000L);
    }

    @Test
    @DisplayName("Should resolve player names via registration, database, and unknown fallback")
    void shouldResolvePlayerNames() throws SQLException {
        UUID knownUuid = UUID.randomUUID();
        UUID dbUuid = UUID.randomUUID();
        UUID unknownUuid = UUID.randomUUID();

        // 1. Manually registered in cache
        leaderboardManager.registerPlayerName(knownUuid, "SpeedyRacer");
        assertThat(leaderboardManager.resolvePlayerName(knownUuid)).isEqualTo("SpeedyRacer");

        // 2. Saved in database `dana_players` table
        databaseManager.getStorageProvider().savePlayer(dbUuid, "TrackMaster");
        assertThat(leaderboardManager.resolvePlayerName(dbUuid)).isEqualTo("TrackMaster");

        // 3. Unknown player fallback
        assertThat(leaderboardManager.resolvePlayerName(unknownUuid)).isEqualTo("Inconnu");
        assertThat(leaderboardManager.resolvePlayerName(null)).isEqualTo("Inconnu");
    }

    @Test
    @DisplayName("Should reset ranking and invalidate cache")
    void shouldResetRankingAndInvalidateCache() throws ExecutionException, InterruptedException {
        String trackId = "reset_track";
        UUID p1 = UUID.randomUUID();
        String currentMonth = leaderboardManager.getCurrentPeriodMonth();

        leaderboardManager.recordTime(trackId, p1, 55000L, 2).get();
        assertThat(leaderboardManager.getTopMonthly(trackId, 10).get()).hasSize(1);

        int deleted = leaderboardManager.resetRanking(trackId, currentMonth).get();
        assertThat(deleted).isEqualTo(1);

        assertThat(leaderboardManager.getTopMonthly(trackId, 10).get()).isEmpty();
        assertThat(leaderboardManager.getCachedTop1Monthly(trackId)).isEmpty();
    }
}
