package fr.danakube.danaevent.modules.chromaticsheep.task;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.database.DatabaseConfig;
import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.chromaticsheep.config.ArenaConfig;
import fr.danakube.danaevent.modules.chromaticsheep.database.ChromaticSheepDatabase;
import fr.danakube.danaevent.modules.chromaticsheep.manager.ArenaManager;
import fr.danakube.danaevent.modules.chromaticsheep.manager.HerdManager;
import fr.danakube.danaevent.modules.chromaticsheep.manager.SheepGameManager;
import fr.danakube.danaevent.modules.chromaticsheep.manager.SheepScoreManager;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameFormat;
import fr.danakube.danaevent.modules.chromaticsheep.model.PlayerSheepSession;
import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepArena;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepData;
import fr.danakube.danaevent.modules.chromaticsheep.model.SpecialSheepType;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Sheep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class DominationTickTaskTest {

    private ServerMock server;
    private WorldMock world;
    private DanaEventPlugin plugin;
    private DatabaseManager databaseManager;
    private SheepGameManager gameManager;
    private HerdManager herdManager;
    private SheepScoreManager scoreManager;
    private final String arenaId = "domination_arena";

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws SQLException {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("domination_world");
        plugin = MockBukkit.load(DanaEventPlugin.class);

        DatabaseConfig dbConfig = new DatabaseConfig(StorageType.SQLITE, "localhost", 3306, "test_dom.db", "", "", 5);
        databaseManager = new DatabaseManager(dbConfig, tempDir.toFile());
        ChromaticSheepDatabase database = new ChromaticSheepDatabase(databaseManager, Runnable::run);
        database.initTables();

        ArenaConfig arenaConfig = new ArenaConfig(new File(tempDir.toFile(), "arenas.yml"));
        ArenaManager arenaManager = new ArenaManager(arenaConfig);
        herdManager = new HerdManager();
        scoreManager = new SheepScoreManager();

        SheepArena arena = arenaManager.createArena(arenaId, "Domination Arena", GameFormat.SOLO, ScoringMode.DOMINATION_TICK);
        arena.setDurationSeconds(60);
        arena.setSheepCount(5);
        arena.setBounds(new CuboidRegion(new Location(world, 0, 60, 0), new Location(world, 20, 70, 20)));
        arena.addPlayerSpawn(new Location(world, 10, 61, 10));

        gameManager = new SheepGameManager(
            plugin,
            plugin.getPlayerStateManager(),
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
    @DisplayName("DominationTickTask awards points based on colored sheep alive in arena")
    void shouldAwardDominationPoints() {
        PlayerMock player = server.addPlayer();
        gameManager.joinGame(player, arenaId);
        gameManager.startGame(arenaId);

        PlayerSheepSession session = gameManager.getSession(player.getUniqueId()).orElseThrow();
        DyeColor playerColor = session.getColor();

        // Spawn a regular sheep colored with player's color
        Sheep s1 = (Sheep) world.spawnEntity(new Location(world, 5, 64, 5), EntityType.SHEEP);
        SheepData.tagSheep(s1, arenaId, SpecialSheepType.NORMAL);
        s1.setColor(playerColor);
        herdManager.trackSheep(arenaId, s1.getUniqueId());

        // Spawn a golden sheep colored with player's color
        Sheep s2 = (Sheep) world.spawnEntity(new Location(world, 6, 64, 6), EntityType.SHEEP);
        SheepData.tagSheep(s2, arenaId, SpecialSheepType.GOLDEN);
        s2.setColor(playerColor);
        herdManager.trackSheep(arenaId, s2.getUniqueId());

        DominationTickTask task = new DominationTickTask(gameManager, herdManager, scoreManager, arenaId);
        task.run();

        // 1 regular (+1 pt) + 1 golden (+5 pts) = 6 pts
        assertThat(scoreManager.getScore(arenaId, player.getUniqueId())).isEqualTo(6);

        // Run another tick -> 12 pts
        task.run();
        assertThat(scoreManager.getScore(arenaId, player.getUniqueId())).isEqualTo(12);
    }
}
