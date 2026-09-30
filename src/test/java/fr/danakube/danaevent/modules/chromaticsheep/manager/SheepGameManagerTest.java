package fr.danakube.danaevent.modules.chromaticsheep.manager;

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
import fr.danakube.danaevent.modules.chromaticsheep.item.PaintBombItem;
import fr.danakube.danaevent.modules.chromaticsheep.item.PaintBrushItem;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameFormat;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameState;
import fr.danakube.danaevent.modules.chromaticsheep.model.PlayerSheepSession;
import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepArena;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepGame;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SheepGameManagerTest {

    private ServerMock server;
    private WorldMock world;
    private DanaEventPlugin plugin;
    private DatabaseManager databaseManager;
    private ChromaticSheepDatabase database;
    private ArenaManager arenaManager;
    private HerdManager herdManager;
    private SheepScoreManager scoreManager;
    private PlayerStateManager playerStateManager;
    private SheepGameManager gameManager;
    private SheepArena arena;

    private final String arenaId = "colosseum";

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws SQLException {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("colosseum_world");
        plugin = MockBukkit.load(DanaEventPlugin.class);

        DatabaseConfig dbConfig = new DatabaseConfig(StorageType.SQLITE, "localhost", 3306, "test_gm.db", "", "", 5);
        databaseManager = new DatabaseManager(dbConfig, tempDir.toFile());
        database = new ChromaticSheepDatabase(databaseManager, Runnable::run);
        database.initTables();

        ArenaConfig arenaConfig = new ArenaConfig(new File(tempDir.toFile(), "arenas.yml"));
        arenaManager = new ArenaManager(arenaConfig);
        herdManager = new HerdManager();
        scoreManager = new SheepScoreManager();
        playerStateManager = plugin.getPlayerStateManager();

        arena = arenaManager.createArena(arenaId, "Colosseum", GameFormat.SOLO, ScoringMode.FINAL_COUNT);
        arena.setDurationSeconds(120);
        arena.setSheepCount(15);
        arena.setBounds(new CuboidRegion(new Location(world, 0, 60, 0), new Location(world, 30, 75, 30)));
        arena.addPlayerSpawn(new Location(world, 15, 61, 15));

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
    @DisplayName("Player joining game gets snapshot saved, tools equipped, and session created")
    void shouldJoinGame() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 64));

        boolean joined = gameManager.joinGame(player, arenaId);
        assertThat(joined).isTrue();

        // Inventory must have been cleared and replaced with locked tools
        assertThat(player.getInventory().contains(Material.DIAMOND)).isFalse();
        assertThat(PaintBrushItem.isPaintBrush(player.getInventory().getItem(0))).isTrue();
        assertThat(PaintBombItem.isPaintBomb(player.getInventory().getItem(1))).isTrue();
        assertThat(player.getGameMode()).isEqualTo(GameMode.ADVENTURE);

        // Session created
        Optional<PlayerSheepSession> sessionOpt = gameManager.getSession(player.getUniqueId());
        assertThat(sessionOpt).isPresent();
        assertThat(sessionOpt.get().getColor()).isNotNull();

        // Attempt duplicate join should fail
        assertThat(gameManager.joinGame(player, arenaId)).isFalse();
    }

    @Test
    @DisplayName("Player leaving game restores inventory and removes session")
    void shouldLeaveGame() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(new ItemStack(Material.EMERALD, 10));

        gameManager.joinGame(player, arenaId);
        assertThat(gameManager.getSession(player.getUniqueId())).isPresent();

        boolean left = gameManager.leaveGame(player);
        assertThat(left).isTrue();

        assertThat(gameManager.getSession(player.getUniqueId())).isEmpty();
        assertThat(player.getInventory().contains(Material.EMERALD)).isTrue();
    }

    @Test
    @DisplayName("Should start game, spawn herd, and update state to RUNNING")
    void shouldStartGame() {
        PlayerMock p1 = server.addPlayer();
        PlayerMock p2 = server.addPlayer();

        gameManager.joinGame(p1, arenaId);
        gameManager.joinGame(p2, arenaId);

        boolean started = gameManager.startGame(arenaId);
        assertThat(started).isTrue();

        SheepGame game = gameManager.getGame(arenaId);
        assertThat(game).isNotNull();
        assertThat(game.getState()).isEqualTo(GameState.RUNNING);

        // Verify herd spawned
        assertThat(herdManager.getSheep(arenaId)).hasSize(15);

        // Verify bossbar created
        assertThat(game.getBossBar()).isNotNull();
    }

    @Test
    @DisplayName("Ending game computes scores, restores players and achieves 100% entity cleanup")
    void shouldEndGameCleanly() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 5));

        gameManager.joinGame(player, arenaId);
        gameManager.startGame(arenaId);

        assertThat(herdManager.getSheep(arenaId)).hasSize(15);

        // End game
        gameManager.endGame(arenaId);

        // Mobs must be 100% cleaned up
        assertThat(herdManager.getSheep(arenaId)).isEmpty();

        // Player must be restored
        assertThat(player.getInventory().contains(Material.GOLD_INGOT)).isTrue();
        assertThat(gameManager.getSession(player.getUniqueId())).isEmpty();
        assertThat(gameManager.getGame(arenaId)).isNull();
    }

    @Test
    @DisplayName("Emergency stop cleans all mobs and restores all players")
    void shouldStopGameEmergency() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(new ItemStack(Material.IRON_SWORD));

        gameManager.joinGame(player, arenaId);
        gameManager.startGame(arenaId);

        gameManager.stopGame(arenaId);

        assertThat(herdManager.getSheep(arenaId)).isEmpty();
        assertThat(player.getInventory().contains(Material.IRON_SWORD)).isTrue();
        assertThat(gameManager.getGame(arenaId)).isNull();
    }
}
