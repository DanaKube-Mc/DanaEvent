package fr.danakube.danaevent.modules.chromaticsheep.command;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.chromaticsheep.ChromaticSheepModule;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameFormat;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameState;
import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepArena;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepGame;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class SheepCommandTest {

    private ServerMock server;
    private WorldMock world;
    private DanaEventPlugin plugin;
    private ChromaticSheepModule module;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        world = server.addSimpleWorld("sheep_world");
        module = plugin.getChromaticSheepModule();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private List<String> drainMessages(PlayerMock player) {
        List<String> messages = new ArrayList<>();
        Component comp;
        while ((comp = player.nextComponentMessage()) != null) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(comp));
        }
        return messages;
    }

    private SheepArena createSampleArena(String id, boolean ready) {
        SheepArena arena = new SheepArena(id, "Arène " + id, GameFormat.SOLO, ScoringMode.ACTION_SCORE);
        if (ready) {
            arena.setBounds(new CuboidRegion(world.getName(), -10, 60, -10, 10, 70, 10));
            arena.addPlayerSpawn(new Location(world, 0, 64, 0));
            arena.setSheepCount(10);
            arena.setDurationSeconds(60);
        }
        module.getArenaManager().registerArena(arena);
        module.getArenaManager().saveArenas();
        return arena;
    }

    @Nested
    @DisplayName("Player Commands Tests")
    class PlayerCommands {

        @Test
        @DisplayName("/de mc list displays arenas with statuses")
        void shouldListArenas() {
            createSampleArena("arena_ready", true);
            createSampleArena("arena_unready", false);

            PlayerMock player = server.addPlayer("Alice");
            server.dispatchCommand(player, "de mc list");

            List<String> messages = drainMessages(player);
            assertThat(messages).anyMatch(m -> m.contains("arena_ready") && (m.contains("Disponible") || m.contains("Prête")));
            assertThat(messages).anyMatch(m -> m.contains("arena_unready") && m.contains("Non configurée"));
        }

        @Test
        @DisplayName("/de mc join enlists player into the arena")
        void shouldJoinArena() {
            createSampleArena("pasture", true);

            PlayerMock player = server.addPlayer("Bob");
            server.dispatchCommand(player, "de mc join pasture");

            assertThat(module.getGameManager().getSession(player.getUniqueId())).isPresent();
            assertThat(plugin.getPlayerStateManager().hasSnapshot(player.getUniqueId())).isTrue();
        }

        @Test
        @DisplayName("/de mc join fails when arena unknown or not ready")
        void shouldFailJoinUnknownOrNotReady() {
            createSampleArena("incomplete", false);

            PlayerMock player = server.addPlayer("Charlie");
            server.dispatchCommand(player, "de mc join unknown_arena");
            List<String> msgs1 = drainMessages(player);
            assertThat(msgs1).anyMatch(m -> m.contains("introuvable"));

            server.dispatchCommand(player, "de mc join incomplete");
            List<String> msgs2 = drainMessages(player);
            assertThat(msgs2).anyMatch(m -> m.contains("complètement configurée"));
        }

        @Test
        @DisplayName("/de mc leave restores player state and leaves game")
        void shouldLeaveArena() {
            createSampleArena("pasture", true);

            PlayerMock player = server.addPlayer("David");
            server.dispatchCommand(player, "de mc join pasture");
            assertThat(module.getGameManager().getSession(player.getUniqueId())).isPresent();

            server.dispatchCommand(player, "de mc leave");
            assertThat(module.getGameManager().getSession(player.getUniqueId())).isEmpty();
            assertThat(plugin.getPlayerStateManager().hasSnapshot(player.getUniqueId())).isFalse();
        }

        @Test
        @DisplayName("/de mc top shows leaderboard or empty message")
        void shouldShowLeaderboard() throws ExecutionException, InterruptedException {
            createSampleArena("pasture", true);
            PlayerMock player = server.addPlayer("Eve");

            server.dispatchCommand(player, "de mc top pasture");
            Thread.sleep(150);
            List<String> msgs1 = drainMessages(player);
            assertThat(msgs1).anyMatch(m -> m.contains("Aucun record"));

            module.getDatabase().saveRecord(new SheepRecord(
                "pasture", player.getUniqueId(), false, 75, 30,
                ScoringMode.ACTION_SCORE, module.getLeaderboardManager().getCurrentPeriodMonth(), Instant.now()
            )).get();
            module.getLeaderboardManager().invalidateAll();

            server.dispatchCommand(player, "de mc top pasture");
            Thread.sleep(150);
            List<String> msgs2 = drainMessages(player);
            assertThat(msgs2).anyMatch(m -> m.contains("Classement"));
            assertThat(msgs2).anyMatch(m -> m.contains("Eve") && m.contains("75 pts"));
        }
    }

    @Nested
    @DisplayName("Admin Commands Tests")
    class AdminCommands {

        @Test
        @DisplayName("Admin commands require danaevent.chromaticsheep.admin permission")
        void shouldRequirePermission() {
            PlayerMock player = server.addPlayer("NormalUser");
            server.dispatchCommand(player, "de mc admin arena create test SOLO ACTION");

            List<String> messages = drainMessages(player);
            assertThat(messages).anyMatch(m -> m.contains("permission"));
        }

        @Test
        @DisplayName("/de mc admin arena create creates and persists new arena")
        void shouldCreateArena() {
            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.chromaticsheep.admin", true);

            server.dispatchCommand(admin, "de mc admin arena create meadow SOLO ACTION");

            List<String> messages = drainMessages(admin);
            assertThat(messages).anyMatch(m -> m.contains("créée avec succès"));

            assertThat(module.getArenaManager().getArena("meadow")).isPresent();
            SheepArena arena = module.getArenaManager().getArena("meadow").get();
            assertThat(arena.getFormat()).isEqualTo(GameFormat.SOLO);
            assertThat(arena.getScoringMode()).isEqualTo(ScoringMode.ACTION_SCORE);
        }

        @Test
        @DisplayName("/de mc admin arena <id> setbounds uses Wand selection")
        void shouldSetBoundsFromWand() {
            createSampleArena("meadow", false);
            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.chromaticsheep.admin", true);

            plugin.getSelectionManager().setPos1(admin.getUniqueId(), new Location(world, 0, 60, 0));
            plugin.getSelectionManager().setPos2(admin.getUniqueId(), new Location(world, 20, 80, 20));

            server.dispatchCommand(admin, "de mc admin arena meadow setbounds");

            List<String> messages = drainMessages(admin);
            assertThat(messages).anyMatch(m -> m.contains("Zone de l'arène") && m.contains("mise à jour"));

            SheepArena arena = module.getArenaManager().getArena("meadow").get();
            assertThat(arena.getBounds()).isNotNull();
            assertThat(arena.getBounds().contains(10, 70, 10)).isTrue();
        }

        @Test
        @DisplayName("/de mc admin arena <id> addplayerspawn, setspawnsheep, setduration")
        void shouldConfigureArenaProperties() {
            createSampleArena("meadow", false);
            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.chromaticsheep.admin", true);
            admin.teleport(new Location(world, 5, 64, 5));

            server.dispatchCommand(admin, "de mc admin arena meadow addplayerspawn");
            server.dispatchCommand(admin, "de mc admin arena meadow setspawnsheep 25");
            server.dispatchCommand(admin, "de mc admin arena meadow setduration 90");

            SheepArena arena = module.getArenaManager().getArena("meadow").get();
            assertThat(arena.getPlayerSpawns()).hasSize(1);
            assertThat(arena.getSheepCount()).isEqualTo(25);
            assertThat(arena.getDurationSeconds()).isEqualTo(90);
        }

        @Test
        @DisplayName("/de mc admin resetranking deletes arena records")
        void shouldResetRanking() throws ExecutionException, InterruptedException {
            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.chromaticsheep.admin", true);

            createSampleArena("glade", true);
            module.getDatabase().saveRecord(new SheepRecord(
                "glade", admin.getUniqueId(), false, 50, 20,
                ScoringMode.FINAL_COUNT, "2026-09", Instant.now()
            )).get();

            server.dispatchCommand(admin, "de mc admin resetranking glade");
            Thread.sleep(150);

            List<String> messages = drainMessages(admin);
            assertThat(messages).anyMatch(m -> m.contains("réinitialisés"));

            assertThat(module.getLeaderboardManager().getTopAllTime("glade", 5).get()).isEmpty();
        }

        @Test
        @DisplayName("Tab completion suggests subcommands and options")
        void shouldTabCompleteProperly() {
            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.chromaticsheep.admin", true);
            createSampleArena("valley", true);

            List<String> completions1 = server.getCommandTabComplete(admin, "de mc ");
            assertThat(completions1).contains("list", "join", "leave", "top", "admin");

            List<String> completions2 = server.getCommandTabComplete(admin, "de mc admin ");
            assertThat(completions2).contains("arena", "resetranking");

            List<String> completions3 = server.getCommandTabComplete(admin, "de mc admin arena valley ");
            assertThat(completions3).contains("setbounds", "addplayerspawn", "setspawnsheep", "setduration", "start", "stop");
        }
    }

    @Nested
    @DisplayName("End-to-End Match Flow")
    class EndToEndFlow {

        @Test
        @DisplayName("Full match lifecycle: join -> start -> stop cleans up all sheep")
        void shouldExecuteFullMatchLifecycle() {
            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.chromaticsheep.admin", true);

            SheepArena arena = createSampleArena("colosseum", true);
            PlayerMock player1 = server.addPlayer("Fighter1");
            PlayerMock player2 = server.addPlayer("Fighter2");

            // Players join
            server.dispatchCommand(player1, "de mc join colosseum");
            server.dispatchCommand(player2, "de mc join colosseum");

            SheepGame game = module.getGameManager().getGame("colosseum");
            assertThat(game).isNotNull();
            assertThat(game.getSessions()).hasSize(2);

            // Admin starts match
            server.dispatchCommand(admin, "de mc admin arena colosseum start");
            assertThat(game.getState()).isEqualTo(GameState.RUNNING);

            // Verify sheep were spawned in world
            long sheepCount = world.getEntities().stream()
                .filter(e -> e.getType() == EntityType.SHEEP)
                .count();
            assertThat(sheepCount).isEqualTo(10);

            // Admin stops match
            server.dispatchCommand(admin, "de mc admin arena colosseum stop");

            // Absolute mob cleanup guarantee: 0 sheep remaining in world
            long remainingSheep = world.getEntities().stream()
                .filter(e -> e.getType() == EntityType.SHEEP)
                .count();
            assertThat(remainingSheep).isEqualTo(0);

            // Both players' inventories restored
            assertThat(module.getGameManager().getSession(player1.getUniqueId())).isEmpty();
            assertThat(module.getGameManager().getSession(player2.getUniqueId())).isEmpty();
            assertThat(plugin.getPlayerStateManager().hasSnapshot(player1.getUniqueId())).isFalse();
            assertThat(plugin.getPlayerStateManager().hasSnapshot(player2.getUniqueId())).isFalse();
        }
    }
}
