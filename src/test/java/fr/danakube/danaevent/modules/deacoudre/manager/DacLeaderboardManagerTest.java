package fr.danakube.danaevent.modules.deacoudre.manager;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.database.DatabaseConfig;
import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.modules.deacoudre.database.DeACoudreDatabase;
import fr.danakube.danaevent.modules.deacoudre.model.DacRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DacLeaderboardManagerTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private DatabaseManager databaseManager;
    private DeACoudreDatabase database;
    private DacLeaderboardManager leaderboardManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws SQLException {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);

        DatabaseConfig dbConfig = new DatabaseConfig(StorageType.SQLITE, "localhost", 3306, "test_dac_lb.db", "", "", 5);
        databaseManager = new DatabaseManager(dbConfig, tempDir.toFile());
        database = new DeACoudreDatabase(databaseManager, Runnable::run);
        database.initTables();

        leaderboardManager = new DacLeaderboardManager(plugin, database, 5000L);
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should query, cache, resolve names and reset rankings properly")
    void testLeaderboardQueriesAndCache() {
        PlayerMock p1 = server.addPlayer("ChampionDAC");
        PlayerMock p2 = server.addPlayer("RunnerUp");

        database.recordMatchResult("arena_lb", p1.getUniqueId(), false, true, 5, 2).join();
        database.recordMatchResult("arena_lb", p2.getUniqueId(), false, false, 3, 0).join();

        // Top monthly
        List<DacRecord> top = leaderboardManager.getTopMonthly("arena_lb", 10).join();
        assertThat(top).hasSize(2);
        assertThat(top.get(0).holderUuid()).isEqualTo(p1.getUniqueId());
        assertThat(top.get(0).wins()).isEqualTo(1);
        assertThat(top.get(0).perfectDacs()).isEqualTo(2);

        // Name resolution
        String name1 = leaderboardManager.resolveHolderName(p1.getUniqueId(), false);
        assertThat(name1).isEqualTo("ChampionDAC");

        // Cached queries
        var cachedMonthly = leaderboardManager.getCachedTop1Monthly("arena_lb");
        assertThat(cachedMonthly).isPresent();
        assertThat(cachedMonthly.get().holderUuid()).isEqualTo(p1.getUniqueId());

        // Reset ranking
        int resetCount = leaderboardManager.resetRanking("arena_lb", null).join();
        assertThat(resetCount).isEqualTo(2);

        top = leaderboardManager.getTopMonthly("arena_lb", 10).join();
        assertThat(top).isEmpty();
    }
}
