package fr.danakube.danaevent.modules.deacoudre.task;

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
import fr.danakube.danaevent.modules.deacoudre.config.DacConfig;
import fr.danakube.danaevent.modules.deacoudre.database.DeACoudreDatabase;
import fr.danakube.danaevent.modules.deacoudre.manager.DacArenaManager;
import fr.danakube.danaevent.modules.deacoudre.manager.DacGameManager;
import fr.danakube.danaevent.modules.deacoudre.manager.DacPoolManager;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacGame;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameFormat;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameState;
import fr.danakube.danaevent.modules.deacoudre.model.DacPlayerSession;
import fr.danakube.danaevent.modules.deacoudre.model.JumpMode;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class JumpCountdownTaskTest {

    private ServerMock server;
    private WorldMock world;
    private DanaEventPlugin plugin;
    private DatabaseManager databaseManager;
    private DeACoudreDatabase database;
    private DacArenaManager arenaManager;
    private DacPoolManager poolManager;
    private PlayerStateManager playerStateManager;
    private DacGameManager gameManager;
    private DacArena arena;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws SQLException {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("countdown_world");
        plugin = MockBukkit.load(DanaEventPlugin.class);

        DatabaseConfig dbConfig = new DatabaseConfig(StorageType.SQLITE, "localhost", 3306, "test_cd.db", "", "", 5);
        databaseManager = new DatabaseManager(dbConfig, tempDir.toFile());
        database = new DeACoudreDatabase(databaseManager, Runnable::run);
        database.initTables();

        DacConfig dacConfig = new DacConfig(new File(tempDir.toFile(), "dac_arenas.yml"));
        arenaManager = new DacArenaManager(dacConfig);
        poolManager = new DacPoolManager();
        playerStateManager = plugin.getPlayerStateManager();

        arena = arenaManager.createArena("cd_arena", "Countdown Arena", DacGameFormat.SOLO, JumpMode.TURN_BY_TURN);
        arena.setInitialLives(3);
        arena.setJumpTimeSeconds(15);
        arena.setPoolRegion(new CuboidRegion(world.getName(), -2, 50, -2, 2, 50, 2));
        arena.setDivingLocation(new Location(world, 0, 70, 0));
        arena.setLobbyLocation(new Location(world, 10, 60, 10));

        gameManager = new DacGameManager(plugin, playerStateManager, arenaManager, poolManager, database, null);
    }

    @AfterEach
    void tearDown() {
        if (gameManager != null) {
            gameManager.cleanUpAll();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should initialize BossBar, decrease remaining seconds and update color gradient")
    void testCountdownProgression() {
        PlayerMock player = server.addPlayer("Jumper");
        gameManager.joinGame(player, arena.getId());
        DacGame game = gameManager.getGame(arena.getId());
        assertThat(game).isNotNull();
        game.setState(DacGameState.IN_GAME);
        DacPlayerSession session = game.getSession(player.getUniqueId());
        assertThat(session).isNotNull();
        session.setJumping(true);

        JumpCountdownTask task = new JumpCountdownTask(game, gameManager, player.getUniqueId(), 10);
        BossBar bossBar = task.getBossBar();
        assertThat(bossBar.color()).isEqualTo(BossBar.Color.GREEN);
        assertThat(task.getRemainingSeconds()).isEqualTo(10);

        // Run 5 ticks (simulating 5 seconds passing)
        for (int i = 0; i < 5; i++) {
            task.run();
        }
        assertThat(task.getRemainingSeconds()).isEqualTo(5);
        assertThat(bossBar.color()).isEqualTo(BossBar.Color.YELLOW);

        // Run 3 more seconds
        for (int i = 0; i < 3; i++) {
            task.run();
        }
        assertThat(task.getRemainingSeconds()).isEqualTo(2);
        assertThat(bossBar.color()).isEqualTo(BossBar.Color.RED);

        task.cancelAndClean();
    }

    @Test
    @DisplayName("Should trigger timeout and deduct life when countdown reaches 0")
    void testTimeoutTrigger() {
        PlayerMock p1 = server.addPlayer("SlowP1");
        PlayerMock p2 = server.addPlayer("WaitingP2");

        gameManager.joinGame(p1, arena.getId());
        gameManager.joinGame(p2, arena.getId());
        gameManager.startGame(arena.getId());

        DacGame game = gameManager.getGame(arena.getId());
        assertThat(game).isNotNull();

        DacPlayerSession s1 = game.getSession(p1.getUniqueId());
        assertThat(s1).isNotNull();
        s1.setJumping(true);

        JumpCountdownTask task = new JumpCountdownTask(game, gameManager, p1.getUniqueId(), 2);
        task.run(); // 1s left
        assertThat(s1.getLives()).isEqualTo(3);

        task.run(); // 0s left -> triggers timeout!
        assertThat(s1.getLives()).isEqualTo(2);
    }
}
