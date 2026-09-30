package fr.danakube.danaevent.modules.deacoudre.command;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.deacoudre.DeACoudreModule;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacGame;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameFormat;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameState;
import fr.danakube.danaevent.modules.deacoudre.model.JumpMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DacCommandTest {

    private ServerMock server;
    private WorldMock world;
    private DanaEventPlugin plugin;
    private DeACoudreModule module;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("cmd_dac_world");
        plugin = MockBukkit.load(DanaEventPlugin.class);
        module = (DeACoudreModule) plugin.getModuleManager().getModule("deacoudre").orElseThrow();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Nested
    @DisplayName("Player Commands Tests")
    class PlayerCommandsTests {

        @Test
        @DisplayName("/de dac list should display existing arenas")
        void testList() {
            PlayerMock player = server.addPlayer("Observer");
            player.performCommand("de dac list");

            // Empty list initially
            DacArena arena = module.getArenaManager().createArena("arena_list", "Arena List", DacGameFormat.SOLO, JumpMode.TURN_BY_TURN);
            player.performCommand("de dac list");
            assertThat(module.getArenaManager().getArena("arena_list")).isPresent();
        }

        @Test
        @DisplayName("/de dac join and /de dac leave should work properly")
        void testJoinAndLeave() {
            DacArena arena = module.getArenaManager().createArena("ready_arena", "Ready Arena", DacGameFormat.SOLO, JumpMode.TURN_BY_TURN);
            arena.setPoolRegion(new CuboidRegion(world.getName(), -1, 50, -1, 1, 50, 1));
            arena.setDivingLocation(new Location(world, 0, 70, 0));
            arena.setLobbyLocation(new Location(world, 10, 60, 10));

            PlayerMock player = server.addPlayer("JumperPlayer");
            player.performCommand("de dac join ready_arena");

            assertThat(module.getGameManager().getSession(player.getUniqueId())).isPresent();

            player.performCommand("de dac leave");
            assertThat(module.getGameManager().getSession(player.getUniqueId())).isEmpty();
        }

        @Test
        @DisplayName("/de dac top should execute without errors")
        void testTop() {
            PlayerMock player = server.addPlayer("TopViewer");
            DacArena arena = module.getArenaManager().createArena("top_arena", "Top Arena", DacGameFormat.SOLO, JumpMode.TURN_BY_TURN);

            player.performCommand("de dac top top_arena monthly");
            player.performCommand("de dac top top_arena alltime");
        }
    }

    @Nested
    @DisplayName("Admin Commands Tests")
    class AdminCommandsTests {

        @Test
        @DisplayName("Admin command should require danaevent.deacoudre.admin permission")
        void testAdminPermission() {
            PlayerMock regular = server.addPlayer("RegularUser");
            regular.performCommand("de dac admin arena create test_perm");
            assertThat(module.getArenaManager().getArena("test_perm")).isEmpty();

            PlayerMock admin = server.addPlayer("AdminUser");
            admin.addAttachment(plugin, DacAdminCmd.PERMISSION, true);
            admin.performCommand("de dac admin arena create test_perm SOLO TURN_BY_TURN");
            assertThat(module.getArenaManager().getArena("test_perm")).isPresent();
        }

        @Test
        @DisplayName("Admin setup flow: create, setpool, setdiving, setlobby, setlives, setjumptime, start and stop")
        void testAdminSetupFlow() {
            PlayerMock admin = server.addPlayer("SuperAdmin");
            admin.addAttachment(plugin, DacAdminCmd.PERMISSION, true);

            // 1. Create
            admin.performCommand("de dac admin arena create titan SOLO TURN_BY_TURN");
            DacArena arena = module.getArenaManager().getArena("titan").orElseThrow();

            // 2. Setpool with wand selection
            plugin.getSelectionManager().setPos1(admin.getUniqueId(), new Location(world, -2, 50, -2));
            plugin.getSelectionManager().setPos2(admin.getUniqueId(), new Location(world, 2, 50, 2));
            admin.performCommand("de dac admin arena titan setpool");
            assertThat(arena.getPoolRegion()).isNotNull();

            // 3. Setdiving & setlobby
            admin.teleport(new Location(world, 0, 75, 0));
            admin.performCommand("de dac admin arena titan setdiving");
            assertThat(arena.getDivingLocation()).isNotNull();

            admin.teleport(new Location(world, 15, 65, 15));
            admin.performCommand("de dac admin arena titan setlobby");
            assertThat(arena.getLobbyLocation()).isNotNull();

            // 4. Setlives & setjumptime
            admin.performCommand("de dac admin arena titan setlives 4");
            assertThat(arena.getInitialLives()).isEqualTo(4);

            admin.performCommand("de dac admin arena titan setjumptime 20");
            assertThat(arena.getJumpTimeSeconds()).isEqualTo(20);

            // 5. Fill pool with water
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    world.getBlockAt(x, 50, z).setType(Material.WATER);
                }
            }

            // 6. Join 2 players and start
            PlayerMock p1 = server.addPlayer("P1");
            PlayerMock p2 = server.addPlayer("P2");
            p1.performCommand("de dac join titan");
            p2.performCommand("de dac join titan");

            admin.performCommand("de dac admin arena titan start");
            DacGame game = module.getGameManager().getGame("titan");
            assertThat(game).isNotNull();
            assertThat(game.getState()).isEqualTo(DacGameState.IN_GAME);

            // 7. Stop
            admin.performCommand("de dac admin arena titan stop");
            assertThat(module.getGameManager().getGame("titan")).isNull();

            // 8. Resetranking
            admin.performCommand("de dac admin resetranking titan");
        }
    }

    @Nested
    @DisplayName("Tab Completer Tests")
    class TabCompleterTests {

        @Test
        @DisplayName("Tab completion should provide relevant suggestions according to sender and args")
        void testTabCompletion() {
            PlayerMock admin = server.addPlayer("AdminTab");
            admin.addAttachment(plugin, DacAdminCmd.PERMISSION, true);

            List<String> rootSubs = plugin.getCommandManager().onTabComplete(admin, plugin.getCommand("danaevent"), "de", new String[]{"dac", ""});
            assertThat(rootSubs).contains("list", "join", "leave", "top", "admin");

            List<String> adminSubs = plugin.getCommandManager().onTabComplete(admin, plugin.getCommand("danaevent"), "de", new String[]{"dac", "admin", ""});
            assertThat(adminSubs).contains("arena", "resetranking");
        }
    }

    @Nested
    @DisplayName("E2E Scenario Test")
    class E2EScenarioTest {

        @Test
        @DisplayName("Full E2E: Admin sets up arena, players join, jump success and match concludes")
        void testFullE2E() {
            PlayerMock admin = server.addPlayer("Host");
            admin.addAttachment(plugin, DacAdminCmd.PERMISSION, true);

            admin.performCommand("de dac admin arena create e2e_dac SOLO TURN_BY_TURN");
            DacArena arena = module.getArenaManager().getArena("e2e_dac").orElseThrow();

            plugin.getSelectionManager().setPos1(admin.getUniqueId(), new Location(world, -1, 50, -1));
            plugin.getSelectionManager().setPos2(admin.getUniqueId(), new Location(world, 1, 50, 1));
            admin.performCommand("de dac admin arena e2e_dac setpool");

            admin.teleport(new Location(world, 0, 70, 0));
            admin.performCommand("de dac admin arena e2e_dac setdiving");

            admin.teleport(new Location(world, 10, 60, 10));
            admin.performCommand("de dac admin arena e2e_dac setlobby");

            admin.performCommand("de dac admin arena e2e_dac setlives 1");

            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    world.getBlockAt(x, 50, z).setType(Material.WATER);
                }
            }

            PlayerMock p1 = server.addPlayer("P1_E2E");
            PlayerMock p2 = server.addPlayer("P2_E2E");

            p1.performCommand("de dac join e2e_dac");
            p2.performCommand("de dac join e2e_dac");

            admin.performCommand("de dac admin arena e2e_dac start");

            DacGame game = module.getGameManager().getGame("e2e_dac");
            assertThat(game).isNotNull();

            // P1 jumps and fails
            module.getGameManager().onJumpFail(p1, "Hit wool");

            // Game should finish and P2 should be winner
            assertThat(module.getGameManager().getGame("e2e_dac")).isNull();
            assertThat(world.getBlockAt(0, 50, 0).getType()).isEqualTo(Material.WATER);
        }
    }
}
