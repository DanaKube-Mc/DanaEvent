package fr.danakube.danaevent.modules.deacoudre.manager;

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
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacGame;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameFormat;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameState;
import fr.danakube.danaevent.modules.deacoudre.model.DacPlayerSession;
import fr.danakube.danaevent.modules.deacoudre.model.JumpMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DacGameManagerTest {

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

    private final String arenaId = "olympus_dac";

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws SQLException {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("dac_game_world");
        plugin = MockBukkit.load(DanaEventPlugin.class);

        DatabaseConfig dbConfig = new DatabaseConfig(StorageType.SQLITE, "localhost", 3306, "test_dac_gm.db", "", "", 5);
        databaseManager = new DatabaseManager(dbConfig, tempDir.toFile());
        database = new DeACoudreDatabase(databaseManager, Runnable::run);
        database.initTables();

        DacConfig dacConfig = new DacConfig(new File(tempDir.toFile(), "dac_arenas.yml"));
        arenaManager = new DacArenaManager(dacConfig);
        poolManager = new DacPoolManager();
        playerStateManager = plugin.getPlayerStateManager();

        arena = arenaManager.createArena(arenaId, "Olympus DAC", DacGameFormat.SOLO, JumpMode.TURN_BY_TURN);
        arena.setInitialLives(3);
        arena.setJumpTimeSeconds(15);
        arena.setPoolRegion(new CuboidRegion(world.getName(), -2, 50, -2, 2, 50, 2));
        arena.setDivingLocation(new Location(world, 0, 70, 0, 0, 90));
        arena.setLobbyLocation(new Location(world, 10, 60, 10, 0, 0));

        // Fill pool region with water
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(x, 50, z).setType(Material.WATER);
            }
        }

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
    @DisplayName("Should successfully enroll players, save inventories, assign colors and handle leave")
    void testJoinAndLeaveGame() {
        PlayerMock player = server.addPlayer("Jumper1");
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 5));

        boolean joined = gameManager.joinGame(player, arenaId);
        assertThat(joined).isTrue();

        // Inventory must be saved and cleared
        assertThat(player.getInventory().contains(Material.DIAMOND)).isFalse();
        assertThat(playerStateManager.hasSnapshot(player.getUniqueId())).isTrue();

        Optional<DacPlayerSession> sessionOpt = gameManager.getSession(player.getUniqueId());
        assertThat(sessionOpt).isPresent();
        DacPlayerSession session = sessionOpt.get();
        assertThat(session.getArenaId()).isEqualTo(arenaId);
        assertThat(session.getLives()).isEqualTo(3);
        assertThat(session.getColor()).isNotNull();

        // Player teleported to lobby
        assertThat(player.getLocation().getBlockX()).isEqualTo(10);
        assertThat(player.getLocation().getBlockY()).isEqualTo(60);

        // Leave game restores inventory
        boolean left = gameManager.leaveGame(player);
        assertThat(left).isTrue();
        assertThat(gameManager.getSession(player.getUniqueId())).isEmpty();
        assertThat(player.getInventory().contains(Material.DIAMOND, 5)).isTrue();
    }

    @Test
    @DisplayName("Should start match, cycle turn queue, and handle jump success & turn advance")
    void testStartGameAndJumpSuccess() {
        PlayerMock p1 = server.addPlayer("P1");
        PlayerMock p2 = server.addPlayer("P2");

        gameManager.joinGame(p1, arenaId);
        gameManager.joinGame(p2, arenaId);

        boolean started = gameManager.startGame(arenaId);
        assertThat(started).isTrue();

        DacGame game = gameManager.getGame(arenaId);
        assertThat(game).isNotNull();
        assertThat(game.getState()).isEqualTo(DacGameState.IN_GAME);
        assertThat(game.getCurrentJumper()).isNotNull();

        UUID currentJumperUuid = game.getCurrentJumper();
        PlayerMock currentJumper = currentJumperUuid.equals(p1.getUniqueId()) ? p1 : p2;
        PlayerMock otherPlayer = currentJumper == p1 ? p2 : p1;

        DacPlayerSession jumperSession = game.getSession(currentJumperUuid);
        assertThat(jumperSession).isNotNull();
        assertThat(jumperSession.isJumping()).isTrue();

        // Jumper teleported to diving platform
        assertThat(currentJumper.getLocation().getBlockX()).isEqualTo(0);
        assertThat(currentJumper.getLocation().getBlockY()).isEqualTo(70);

        // Simulate jump success
        Block water = world.getBlockAt(0, 50, 0);
        gameManager.onJumpSuccess(currentJumper, water, false);

        assertThat(jumperSession.isJumping()).isFalse();
        assertThat(jumperSession.getSuccessfulJumps()).isEqualTo(1);
        assertThat(currentJumper.getLocation().getBlockX()).isEqualTo(10); // back to lobby

        // Next turn should advance to other player
        assertThat(game.getCurrentJumper()).isEqualTo(otherPlayer.getUniqueId());
        DacPlayerSession otherSession = game.getSession(otherPlayer.getUniqueId());
        assertThat(otherSession).isNotNull();
        assertThat(otherSession.isJumping()).isTrue();
    }

    @Test
    @DisplayName("Should grant +1 life on Perfect DAC up to max lives")
    void testPerfectDacBonus() {
        PlayerMock p1 = server.addPlayer("PerfectPro");
        PlayerMock p2 = server.addPlayer("Challenger");

        gameManager.joinGame(p1, arenaId);
        gameManager.joinGame(p2, arenaId);
        gameManager.startGame(arenaId);

        DacGame game = gameManager.getGame(arenaId);
        DacPlayerSession s1 = game.getSession(p1.getUniqueId());
        assertThat(s1).isNotNull();

        // Lose 1 life first
        s1.setLives(2);

        // Perform Perfect DAC
        Block water = world.getBlockAt(0, 50, 0);
        gameManager.onJumpSuccess(p1, water, true);

        assertThat(s1.getPerfectDacs()).isEqualTo(1);
        assertThat(s1.getLives()).isEqualTo(3); // Regained 1 life!
    }

    @Test
    @DisplayName("Should eliminate player on 0 lives, conclude match, persist stats and rollback pool")
    void testEliminationAndVictoryFlow() {
        arena.setInitialLives(1);

        PlayerMock p1 = server.addPlayer("UnluckyJumper");
        PlayerMock p2 = server.addPlayer("WinnerSurvivor");

        p1.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 3));
        p2.getInventory().addItem(new ItemStack(Material.IRON_INGOT, 10));

        gameManager.joinGame(p1, arenaId);
        gameManager.joinGame(p2, arenaId);
        gameManager.startGame(arenaId);

        DacGame game = gameManager.getGame(arenaId);
        assertThat(game).isNotNull();

        // Turn blocks into wool
        world.getBlockAt(0, 50, 0).setType(Material.BLUE_WOOL);

        // p1 jumps and fails
        gameManager.onJumpFail(p1, "Hit wool");

        DacPlayerSession s1 = game.getSession(p1.getUniqueId());
        assertThat(s1.getLives()).isEqualTo(0);
        assertThat(s1.isSpectator()).isTrue();

        // Check win condition should now end the game with p2 as winner!
        assertThat(gameManager.getGame(arenaId)).isNull(); // game concluded and removed
        assertThat(p1.getInventory().contains(Material.GOLDEN_APPLE, 3)).isTrue();
        assertThat(p2.getInventory().contains(Material.IRON_INGOT, 10)).isTrue();

        // Pool should be cleanly rolled back to water
        assertThat(world.getBlockAt(0, 50, 0).getType()).isEqualTo(Material.WATER);
        assertThat(poolManager.getRemainingWaterCount(arena, world)).isEqualTo(25);

        // Verify stats persisted in database
        Optional<fr.danakube.danaevent.modules.deacoudre.model.DacRecord> winnerRecord =
            database.getPersonalStats(arenaId, p2.getUniqueId()).join();
        assertThat(winnerRecord).isPresent();
        assertThat(winnerRecord.get().wins()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should cleanly stop game and restore all player states on stopGame")
    void testStopGame() {
        PlayerMock p1 = server.addPlayer("P1");
        p1.getInventory().addItem(new ItemStack(Material.EMERALD, 1));

        gameManager.joinGame(p1, arenaId);
        DacGame game = gameManager.getGame(arenaId);
        assertThat(game).isNotNull();

        gameManager.stopGame(arenaId);

        assertThat(gameManager.getGame(arenaId)).isNull();
        assertThat(p1.getInventory().contains(Material.EMERALD, 1)).isTrue();
    }
}
