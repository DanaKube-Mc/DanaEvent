package fr.danakube.danaevent.modules.chromaticsheep.task;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.database.DatabaseConfig;
import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.core.player.PlayerStateManager;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.chromaticsheep.config.ArenaConfig;
import fr.danakube.danaevent.modules.chromaticsheep.database.ChromaticSheepDatabase;
import fr.danakube.danaevent.modules.chromaticsheep.manager.ArenaManager;
import fr.danakube.danaevent.modules.chromaticsheep.manager.HerdManager;
import fr.danakube.danaevent.modules.chromaticsheep.manager.SheepGameManager;
import fr.danakube.danaevent.modules.chromaticsheep.manager.SheepScoreManager;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameFormat;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameState;
import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepArena;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepGame;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GameTimerTaskTest {

    private ServerMock server;
    private WorldMock world;
    private DanaEventPlugin plugin;
    private DatabaseManager databaseManager;
    private SheepGameManager gameManager;
    private SheepArena arena;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws SQLException {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("timer_world");
        plugin = MockBukkit.load(DanaEventPlugin.class);

        DatabaseConfig dbConfig = new DatabaseConfig(StorageType.SQLITE, "localhost", 3306, "test_timer.db", "", "", 5);
        databaseManager = new DatabaseManager(dbConfig, tempDir.toFile());
        ChromaticSheepDatabase database = new ChromaticSheepDatabase(databaseManager, Runnable::run);
        database.initTables();

        ArenaConfig arenaConfig = new ArenaConfig(new File(tempDir.toFile(), "arenas.yml"));
        ArenaManager arenaManager = new ArenaManager(arenaConfig);
        HerdManager herdManager = new HerdManager();
        SheepScoreManager scoreManager = new SheepScoreManager();
        PlayerStateManager playerStateManager = plugin.getPlayerStateManager();

        arena = arenaManager.createArena("timer_arena", "Timer Arena", GameFormat.SOLO, ScoringMode.FINAL_COUNT);
        arena.setDurationSeconds(10);
        arena.setSheepCount(5);
        arena.setBounds(new CuboidRegion(new Location(world, 0, 60, 0), new Location(world, 20, 70, 20)));
        arena.addPlayerSpawn(new Location(world, 10, 61, 10));

        gameManager = new SheepGameManager(
            plugin,
            playerStateManager,
            arenaManager,
            herdManager,
            scoreManager,
            database,
            null
        );
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null && databaseManager.isRunning()) {
            databaseManager.shutdown();
        }
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("GameTimerTask decrements remaining seconds, updates bossbar and triggers endgame at 0")
    void shouldDecrementAndEndGame() {
        PlayerMock player = server.addPlayer();
        gameManager.joinGame(player, "timer_arena");
        gameManager.startGame("timer_arena");

        SheepGame game = gameManager.getGame("timer_arena");
        assertThat(game).isNotNull();
        assertThat(game.getState()).isEqualTo(GameState.RUNNING);
        assertThat(game.getRemainingSeconds()).isEqualTo(10);

        GameTimerTask timerTask = new GameTimerTask(gameManager, "timer_arena");

        // Run 5 ticks of the task
        for (int i = 0; i < 5; i++) {
            timerTask.run();
        }

        assertThat(game.getRemainingSeconds()).isEqualTo(5);
        BossBar bossBar = game.getBossBar();
        assertThat(bossBar).isNotNull();
        assertThat(bossBar.progress()).isLessThan(1.0f);
        assertThat(bossBar.color()).isEqualTo(BossBar.Color.RED); // remaining <= 10 -> RED

        // Run remaining 5 ticks
        for (int i = 0; i < 5; i++) {
            timerTask.run();
        }

        // Match should be concluded
        assertThat(gameManager.getGame("timer_arena")).isNull();
    }
}
