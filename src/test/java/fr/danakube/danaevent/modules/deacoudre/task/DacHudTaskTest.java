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
import fr.danakube.danaevent.modules.deacoudre.model.JumpMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class DacHudTaskTest {

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
    private DacHudTask hudTask;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws SQLException {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("hud_world");
        plugin = MockBukkit.load(DanaEventPlugin.class);

        DatabaseConfig dbConfig = new DatabaseConfig(StorageType.SQLITE, "localhost", 3306, "test_hud.db", "", "", 5);
        databaseManager = new DatabaseManager(dbConfig, tempDir.toFile());
        database = new DeACoudreDatabase(databaseManager, Runnable::run);
        database.initTables();

        DacConfig dacConfig = new DacConfig(new File(tempDir.toFile(), "dac_arenas.yml"));
        arenaManager = new DacArenaManager(dacConfig);
        poolManager = new DacPoolManager();
        playerStateManager = plugin.getPlayerStateManager();

        arena = arenaManager.createArena("hud_arena", "HUD Arena", DacGameFormat.SOLO, JumpMode.TURN_BY_TURN);
        arena.setInitialLives(3);
        arena.setJumpTimeSeconds(15);
        arena.setPoolRegion(new CuboidRegion(world.getName(), -1, 50, -1, 1, 50, 1));
        arena.setDivingLocation(new Location(world, 0, 70, 0));
        arena.setLobbyLocation(new Location(world, 10, 60, 10));

        // Fill pool with water (3x3 = 9 blocks)
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.getBlockAt(x, 50, z).setType(Material.WATER);
            }
        }

        gameManager = new DacGameManager(plugin, playerStateManager, arenaManager, poolManager, database, null);
        hudTask = new DacHudTask(gameManager, poolManager);
    }

    @AfterEach
    void tearDown() {
        if (hudTask != null) {
            hudTask.cleanUp();
        }
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
    @DisplayName("Should correctly format visual hearts representation")
    void testFormatHearts() {
        assertThat(DacHudTask.formatHearts(3, 3)).isEqualTo("<red>❤❤❤</red>");
        assertThat(DacHudTask.formatHearts(1, 3)).isEqualTo("<red>❤</red><gray>❤❤</gray>");
        assertThat(DacHudTask.formatHearts(0, 3)).isEqualTo("<dark_gray>ÉLIMINÉ</dark_gray>");
    }

    @Test
    @DisplayName("Should update participant scoreboards with live info")
    void testScoreboardUpdate() {
        PlayerMock p1 = server.addPlayer("ScoreP1");
        PlayerMock p2 = server.addPlayer("ScoreP2");

        gameManager.joinGame(p1, arena.getId());
        gameManager.joinGame(p2, arena.getId());
        gameManager.startGame(arena.getId());

        hudTask.run();

        Scoreboard sb = p1.getScoreboard();
        assertThat(sb).isNotNull();
        Objective obj = sb.getObjective("dac_hud");
        assertThat(obj).isNotNull();
        assertThat(obj.getDisplaySlot()).isEqualTo(DisplaySlot.SIDEBAR);
        assertThat(obj.displayName()).isNotNull();

        hudTask.cleanUp();
    }
}
