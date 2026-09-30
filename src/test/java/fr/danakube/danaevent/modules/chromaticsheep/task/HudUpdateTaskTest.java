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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HudUpdateTaskTest {

    private ServerMock server;
    private WorldMock world;
    private DanaEventPlugin plugin;
    private DatabaseManager databaseManager;
    private SheepGameManager gameManager;
    private SheepScoreManager scoreManager;
    private final String arenaId = "hud_arena";

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws SQLException {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("hud_world");
        plugin = MockBukkit.load(DanaEventPlugin.class);

        DatabaseConfig dbConfig = new DatabaseConfig(StorageType.SQLITE, "localhost", 3306, "test_hud.db", "", "", 5);
        databaseManager = new DatabaseManager(dbConfig, tempDir.toFile());
        ChromaticSheepDatabase database = new ChromaticSheepDatabase(databaseManager, Runnable::run);
        database.initTables();

        ArenaConfig arenaConfig = new ArenaConfig(new File(tempDir.toFile(), "arenas.yml"));
        ArenaManager arenaManager = new ArenaManager(arenaConfig);
        HerdManager herdManager = new HerdManager();
        scoreManager = new SheepScoreManager();

        SheepArena arena = arenaManager.createArena(arenaId, "HUD Arena", GameFormat.SOLO, ScoringMode.FINAL_COUNT);
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
    @DisplayName("Should build ActionBar component with ready status and score")
    void shouldBuildActionBarWhenReady() {
        PlayerSheepSession session = new PlayerSheepSession(UUID.randomUUID(), null, DyeColor.ORANGE, arenaId);
        scoreManager.setScore(arenaId, session.getEffectiveHolderUuid(), 42);

        HudUpdateTask task = new HudUpdateTask(gameManager, scoreManager, arenaId);
        Component actionBar = task.buildActionBar(session);

        String plainText = PlainTextComponentSerializer.plainText().serialize(actionBar);
        assertThat(plainText).contains("Bombe");
        assertThat(plainText).contains("PRÊTE");
        assertThat(plainText).contains("42 pts");
    }

    @Test
    @DisplayName("Should build ActionBar component with cooldown remaining")
    void shouldBuildActionBarWhenOnCooldown() {
        PlayerSheepSession session = new PlayerSheepSession(UUID.randomUUID(), null, DyeColor.ORANGE, arenaId);
        session.recordBombThrow();

        HudUpdateTask task = new HudUpdateTask(gameManager, scoreManager, arenaId);
        Component actionBar = task.buildActionBar(session);

        String plainText = PlainTextComponentSerializer.plainText().serialize(actionBar);
        assertThat(plainText).contains("Bombe");
        assertThat(plainText).contains("s");
    }

    @Test
    @DisplayName("HudUpdateTask executes cleanly for online participating players")
    void shouldExecuteCleanly() {
        PlayerMock player = server.addPlayer();
        gameManager.joinGame(player, arenaId);
        gameManager.startGame(arenaId);

        HudUpdateTask task = new HudUpdateTask(gameManager, scoreManager, arenaId);
        task.run();
    }
}
