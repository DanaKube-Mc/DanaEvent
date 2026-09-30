package fr.danakube.danaevent.modules.deacoudre.listener;

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
import fr.danakube.danaevent.modules.deacoudre.model.DacPlayerSession;
import fr.danakube.danaevent.modules.deacoudre.model.JumpMode;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class DacDivingPlatformListenerTest {

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
    private DacDivingPlatformListener listener;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws SQLException {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("diving_world");
        plugin = MockBukkit.load(DanaEventPlugin.class);

        DatabaseConfig dbConfig = new DatabaseConfig(StorageType.SQLITE, "localhost", 3306, "test_div.db", "", "", 5);
        databaseManager = new DatabaseManager(dbConfig, tempDir.toFile());
        database = new DeACoudreDatabase(databaseManager, Runnable::run);
        database.initTables();

        DacConfig dacConfig = new DacConfig(new File(tempDir.toFile(), "dac_arenas.yml"));
        arenaManager = new DacArenaManager(dacConfig);
        poolManager = new DacPoolManager();
        playerStateManager = plugin.getPlayerStateManager();

        arena = arenaManager.createArena("gate_arena", "Gate Arena", DacGameFormat.SOLO, JumpMode.TURN_BY_TURN);
        arena.setInitialLives(3);
        arena.setJumpTimeSeconds(15);
        arena.setPoolRegion(new CuboidRegion(world.getName(), -2, 50, -2, 2, 50, 2));
        arena.setDivingLocation(new Location(world, 0, 70, 0));
        arena.setLobbyLocation(new Location(world, 10, 60, 10));

        gameManager = new DacGameManager(plugin, playerStateManager, arenaManager, poolManager, database, null);
        listener = new DacDivingPlatformListener(gameManager);
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
    @DisplayName("Should allow active jumper to step on diving board and dive")
    void testActiveJumperAllowed() {
        PlayerMock jumper = server.addPlayer("AllowedJumper");
        PlayerMock waiting = server.addPlayer("WaitingPlayer");

        gameManager.joinGame(jumper, arena.getId());
        gameManager.joinGame(waiting, arena.getId());
        gameManager.startGame(arena.getId());

        DacGame game = gameManager.getGame(arena.getId());
        DacPlayerSession jumperSession = game.getSession(jumper.getUniqueId());
        jumperSession.setJumping(true);

        Location from = new Location(world, 0, 70, 0);
        Location to = new Location(world, 0, 69, 0); // diving downward
        PlayerMoveEvent event = new PlayerMoveEvent(jumper, from, to);

        listener.onPlayerMove(event);
        assertThat(event.isCancelled()).isFalse();
        assertThat(event.getTo()).isEqualTo(to);
    }

    @Test
    @DisplayName("Should block inactive player from stepping on diving board or jumping into pool early")
    void testInactivePlayerBlocked() {
        PlayerMock jumper = server.addPlayer("Jumper");
        PlayerMock waiting = server.addPlayer("Cheater");

        gameManager.joinGame(jumper, arena.getId());
        gameManager.joinGame(waiting, arena.getId());
        gameManager.startGame(arena.getId());

        DacGame game = gameManager.getGame(arena.getId());
        DacPlayerSession waitingSession = game.getSession(waiting.getUniqueId());
        waitingSession.setJumping(false);

        // Try to move to diving board
        Location lobby = arena.getLobbyLocation();
        Location nearDiving = new Location(world, 0.5, 70, 0.5);
        PlayerMoveEvent event = new PlayerMoveEvent(waiting, lobby, nearDiving);

        listener.onPlayerMove(event);
        // Intercepted and reset to lobby
        assertThat(event.getTo()).isEqualTo(arena.getLobbyLocation());

        // Try to jump straight into pool
        Location insidePool = new Location(world, 0, 50, 0);
        PlayerMoveEvent poolEvent = new PlayerMoveEvent(waiting, lobby, insidePool);
        listener.onPlayerMove(poolEvent);
        assertThat(poolEvent.getTo()).isEqualTo(arena.getLobbyLocation());
    }
}
