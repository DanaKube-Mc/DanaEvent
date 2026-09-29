package fr.danakube.danaevent.modules.boatrace;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.command.ConsoleCommandSenderMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.boatrace.gui.BoatRaceLeaderboardGui;
import fr.danakube.danaevent.modules.boatrace.model.Track;
import fr.danakube.danaevent.modules.boatrace.model.TrackMode;
import fr.danakube.danaevent.modules.boatrace.model.TrackType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BoatRaceCommandTest {

    private ServerMock server;
    private WorldMock world;
    private DanaEventPlugin plugin;
    private BoatRaceModule module;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        world = server.addSimpleWorld("race_world");

        Optional<?> opt = plugin.getModuleManager().getModule("boatrace");
        assertThat(opt).isPresent();
        module = (BoatRaceModule) opt.get();
    }

    @AfterEach
    void tearDown() {
        if (module != null && module.getRaceManager() != null) {
            module.getRaceManager().cleanUp();
        }
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

    private List<String> drainConsoleMessages(ConsoleCommandSenderMock console) {
        List<String> messages = new ArrayList<>();
        String msg;
        while ((msg = console.nextMessage()) != null) {
            messages.add(msg);
        }
        return messages;
    }

    @Nested
    @DisplayName("Player Commands: /de br list")
    class ListCommandTests {

        @Test
        @DisplayName("Should notify when no tracks exist")
        void shouldNotifyWhenNoTracks() {
            PlayerMock player = server.addPlayer("Alice");
            server.dispatchCommand(player, "de br list");

            List<String> msgs = drainMessages(player);
            assertThat(msgs).anyMatch(m -> m.contains("Aucun circuit disponible"));
        }

        @Test
        @DisplayName("Should list existing tracks with type, mode, and status")
        void shouldListTracksWhenPresent() {
            Track t1 = module.getTrackManager().createTrack("speedway", "Speed Way", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
            Track t2 = module.getTrackManager().createTrack("oval", "The Oval", TrackType.CIRCUIT_LAPS, TrackMode.EVENT_COMPETITION);
            t2.setLaps(3);

            PlayerMock player = server.addPlayer("Alice");
            server.dispatchCommand(player, "de br list");

            List<String> msgs = drainMessages(player);
            String full = String.join("\n", msgs);
            assertThat(full)
                .contains("Circuits disponibles (2)")
                .contains("speedway")
                .contains("Sprint")
                .contains("oval")
                .contains("3 tours");
        }
    }

    @Nested
    @DisplayName("Player Commands: /de br join <circuit>")
    class JoinCommandTests {

        @Test
        @DisplayName("Should reject join from console")
        void shouldRejectConsole() {
            ConsoleCommandSenderMock console = server.getConsoleSender();
            server.dispatchCommand(console, "de br join speedway");

            List<String> msgs = drainConsoleMessages(console);
            assertThat(msgs).anyMatch(m -> m.contains("joueur"));
        }

        @Test
        @DisplayName("Should reject when track is not found")
        void shouldRejectUnknownTrack() {
            PlayerMock player = server.addPlayer("Alice");
            server.dispatchCommand(player, "de br join unknown");

            List<String> msgs = drainMessages(player);
            assertThat(msgs).anyMatch(m -> m.contains("introuvable"));
        }

        @Test
        @DisplayName("Should reject when track is not ready")
        void shouldRejectIncompleteTrack() {
            module.getTrackManager().createTrack("unfinished", "Unfinished", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);

            PlayerMock player = server.addPlayer("Alice");
            server.dispatchCommand(player, "de br join unfinished");

            List<String> msgs = drainMessages(player);
            assertThat(msgs).anyMatch(m -> m.contains("pas encore prêt"));
        }

        @Test
        @DisplayName("Should successfully start race when track is ready")
        void shouldStartRaceSuccessfully() {
            Track track = module.getTrackManager().createTrack("ready_track", "Ready Track", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
            track.setStartRegion(new CuboidRegion(new Location(world, 0, 60, 0), new Location(world, 2, 62, 2)));
            track.setFinishRegion(new CuboidRegion(new Location(world, 20, 60, 0), new Location(world, 22, 62, 2)));
            track.addSpawnPoint(new Location(world, 1, 61, 1));

            PlayerMock player = server.addPlayer("Racer");
            server.dispatchCommand(player, "de br join ready_track");

            assertThat(module.getRaceManager().isRacing(player.getUniqueId())).isTrue();
            assertThat(player.getVehicle()).isInstanceOf(Boat.class);

            List<String> msgs = drainMessages(player);
            assertThat(msgs).anyMatch(m -> m.contains("C'est parti !"));
        }

        @Test
        @DisplayName("Should reject join when already in race")
        void shouldRejectIfAlreadyRacing() {
            Track track = module.getTrackManager().createTrack("ready_track", "Ready Track", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
            track.setStartRegion(new CuboidRegion(new Location(world, 0, 60, 0), new Location(world, 2, 62, 2)));
            track.setFinishRegion(new CuboidRegion(new Location(world, 20, 60, 0), new Location(world, 22, 62, 2)));
            track.addSpawnPoint(new Location(world, 1, 61, 1));

            PlayerMock player = server.addPlayer("Racer");
            server.dispatchCommand(player, "de br join ready_track");
            drainMessages(player);

            server.dispatchCommand(player, "de br join ready_track");
            List<String> msgs = drainMessages(player);
            assertThat(msgs).anyMatch(m -> m.contains("déjà en pleine course"));
        }
    }

    @Nested
    @DisplayName("Player Commands: /de br leave")
    class LeaveCommandTests {

        @Test
        @DisplayName("Should notify when not racing")
        void shouldNotifyIfNotRacing() {
            PlayerMock player = server.addPlayer("Bob");
            server.dispatchCommand(player, "de br leave");

            List<String> msgs = drainMessages(player);
            assertThat(msgs).anyMatch(m -> m.contains("aucune course"));
        }

        @Test
        @DisplayName("Should cancel race and clean up boat when leave is executed")
        void shouldLeaveActiveRace() {
            Track track = module.getTrackManager().createTrack("ready_track", "Ready Track", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
            track.setStartRegion(new CuboidRegion(new Location(world, 0, 60, 0), new Location(world, 2, 62, 2)));
            track.setFinishRegion(new CuboidRegion(new Location(world, 20, 60, 0), new Location(world, 22, 62, 2)));
            track.addSpawnPoint(new Location(world, 1, 61, 1));

            PlayerMock player = server.addPlayer("Racer");
            server.dispatchCommand(player, "de br join ready_track");
            drainMessages(player);

            server.dispatchCommand(player, "de br leave");

            assertThat(module.getRaceManager().isRacing(player.getUniqueId())).isFalse();
            List<String> msgs = drainMessages(player);
            assertThat(msgs).anyMatch(m -> m.contains("Course annulée"));
        }
    }

    @Nested
    @DisplayName("Player Commands: /de br top <circuit>")
    class TopCommandTests {

        @Test
        @DisplayName("Should open leaderboard GUI with specified scope")
        void shouldOpenLeaderboardGui() {
            Track track = module.getTrackManager().createTrack("ice_circuit", "Ice Circuit", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
            PlayerMock player = server.addPlayer("Spectator");

            server.dispatchCommand(player, "de br top ice_circuit monthly");

            assertThat(player.getOpenInventory().getTopInventory()).isNotNull();
            assertThat(player.getOpenInventory().getTopInventory().getSize()).isEqualTo(BoatRaceLeaderboardGui.SIZE);
        }

        @Test
        @DisplayName("Should reject top command when track not found")
        void shouldRejectWhenTrackNotFound() {
            PlayerMock player = server.addPlayer("Spectator");
            server.dispatchCommand(player, "de br top non_existent");

            List<String> msgs = drainMessages(player);
            assertThat(msgs).anyMatch(m -> m.contains("introuvable"));
        }
    }

    @Nested
    @DisplayName("Admin Commands & Permissions")
    class AdminCommandTests {

        @Test
        @DisplayName("Admin commands should be rejected without 'danaevent.boatrace.admin' permission")
        void shouldRejectUnauthorizedAdmin() {
            PlayerMock player = server.addPlayer("RegularUser");
            server.dispatchCommand(player, "de br admin track create test SPRINT 247");

            List<String> msgs = drainMessages(player);
            assertThat(msgs).anyMatch(m -> m.contains("permission"));
            assertThat(module.getTrackManager().getTrack("test")).isEmpty();
        }

        @Test
        @DisplayName("track create should create track and persist")
        void shouldCreateTrackViaAdminCommand() {
            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.boatrace.admin", true);

            server.dispatchCommand(admin, "de br admin track create monza SPRINT 247");

            Optional<Track> trackOpt = module.getTrackManager().getTrack("monza");
            assertThat(trackOpt).isPresent();
            assertThat(trackOpt.get().getType()).isEqualTo(TrackType.SPRINT);
            assertThat(trackOpt.get().getMode()).isEqualTo(TrackMode.TIME_ATTACK_247);

            List<String> msgs = drainMessages(admin);
            assertThat(msgs).anyMatch(m -> m.contains("créé avec succès"));
        }

        @Test
        @DisplayName("track setstart and setfinish should require wand selection and set regions")
        void shouldSetStartAndFinishRegions() {
            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.boatrace.admin", true);

            Track track = module.getTrackManager().createTrack("silverstone", "Silverstone", TrackType.CIRCUIT_LAPS, TrackMode.TIME_ATTACK_247);

            // 1. Without wand selection -> should fail
            server.dispatchCommand(admin, "de br admin track silverstone setstart");
            List<String> msgs = drainMessages(admin);
            assertThat(msgs).anyMatch(m -> m.contains("sélection complète"));

            // 2. With wand selection -> should succeed
            plugin.getSelectionManager().setPos1(admin.getUniqueId(), new Location(world, 0, 60, 0));
            plugin.getSelectionManager().setPos2(admin.getUniqueId(), new Location(world, 5, 62, 5));
            server.dispatchCommand(admin, "de br admin track silverstone setstart");
            assertThat(track.getStartRegion()).isNotNull();

            // 3. Set finish
            plugin.getSelectionManager().setPos1(admin.getUniqueId(), new Location(world, 20, 60, 20));
            plugin.getSelectionManager().setPos2(admin.getUniqueId(), new Location(world, 25, 62, 25));
            server.dispatchCommand(admin, "de br admin track silverstone setfinish");
            assertThat(track.getFinishRegion()).isNotNull();
        }

        @Test
        @DisplayName("track addspawn, setlaps and togglecollision should update track configuration")
        void shouldUpdateTrackProperties() {
            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.boatrace.admin", true);

            Track track = module.getTrackManager().createTrack("spa", "Spa", TrackType.CIRCUIT_LAPS, TrackMode.TIME_ATTACK_247);

            // Add spawn
            admin.teleport(new Location(world, 10, 65, 10));
            server.dispatchCommand(admin, "de br admin track spa addspawn");
            assertThat(track.getSpawnPoints()).hasSize(1);

            // Set laps
            server.dispatchCommand(admin, "de br admin track spa setlaps 5");
            assertThat(track.getLaps()).isEqualTo(5);

            // Toggle collision
            assertThat(track.isCollisionsEnabled()).isFalse();
            server.dispatchCommand(admin, "de br admin track spa togglecollision");
            assertThat(track.isCollisionsEnabled()).isTrue();
            server.dispatchCommand(admin, "de br admin track spa togglecollision");
            assertThat(track.isCollisionsEnabled()).isFalse();
        }

        @Test
        @DisplayName("event start and stop should broadcast and cancel running races")
        void shouldHandleEventStartAndStop() {
            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.boatrace.admin", true);

            Track track = module.getTrackManager().createTrack("event_track", "Event Track", TrackType.SPRINT, TrackMode.EVENT_COMPETITION);
            track.setStartRegion(new CuboidRegion(new Location(world, 0, 60, 0), new Location(world, 2, 62, 2)));
            track.setFinishRegion(new CuboidRegion(new Location(world, 20, 60, 0), new Location(world, 22, 62, 2)));
            track.addSpawnPoint(new Location(world, 1, 61, 1));

            server.dispatchCommand(admin, "de br admin event start event_track");
            List<String> msgs1 = drainMessages(admin);
            assertThat(msgs1).anyMatch(m -> m.contains("Événement lancé"));

            PlayerMock racer = server.addPlayer("Racer");
            module.getRaceManager().startRace(racer, track);
            assertThat(module.getRaceManager().isRacing(racer.getUniqueId())).isTrue();

            server.dispatchCommand(admin, "de br admin event stop event_track");
            assertThat(module.getRaceManager().isRacing(racer.getUniqueId())).isFalse();
        }

        @Test
        @DisplayName("resetranking should delete records for track")
        void shouldResetRanking() {
            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.boatrace.admin", true);

            module.getTrackManager().createTrack("reset_track", "Reset Track", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
            server.dispatchCommand(admin, "de br admin resetranking reset_track");

            List<String> msgs = drainMessages(admin);
            assertThat(msgs).anyMatch(m -> m.contains("Classement réinitialisé"));
        }
    }

    @Nested
    @DisplayName("Dynamic Tab Completion")
    class TabCompletionTests {

        @Test
        @DisplayName("Root tab complete should suggest 'boatrace' and 'br'")
        void shouldTabCompleteRoot() {
            PlayerMock player = server.addPlayer("Tester");
            List<String> completions = plugin.getCommandManager().onTabComplete(player, null, "de", new String[]{""});
            assertThat(completions).contains("boatrace", "br");
        }

        @Test
        @DisplayName("/de br <tab> should suggest subcommands")
        void shouldTabCompleteModuleSubcommands() {
            PlayerMock player = server.addPlayer("Tester");
            List<String> completions = plugin.getCommandManager().onTabComplete(player, null, "de", new String[]{"br", ""});
            assertThat(completions).contains("list", "join", "leave", "top");
        }

        @Test
        @DisplayName("/de br join <tab> should suggest registered track IDs")
        void shouldTabCompleteJoinTracks() {
            module.getTrackManager().createTrack("track_alpha", "Alpha", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
            module.getTrackManager().createTrack("track_beta", "Beta", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);

            PlayerMock player = server.addPlayer("Tester");
            List<String> completions = plugin.getCommandManager().onTabComplete(player, null, "de", new String[]{"br", "join", ""});
            assertThat(completions).contains("track_alpha", "track_beta");
        }

        @Test
        @DisplayName("/de br admin <tab> should suggest admin sub-actions with permission")
        void shouldTabCompleteAdmin() {
            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.boatrace.admin", true);

            List<String> completions = plugin.getCommandManager().onTabComplete(admin, null, "de", new String[]{"br", "admin", ""});
            assertThat(completions).contains("track", "event", "resetranking");
        }

        @Test
        @DisplayName("/de br admin track <id> <tab> should suggest track edit actions")
        void shouldTabCompleteTrackActions() {
            module.getTrackManager().createTrack("le_mans", "Le Mans", TrackType.CIRCUIT_LAPS, TrackMode.TIME_ATTACK_247);

            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.boatrace.admin", true);

            List<String> completions = plugin.getCommandManager().onTabComplete(admin, null, "de", new String[]{"br", "admin", "track", "le_mans", ""});
            assertThat(completions).contains("setstart", "setfinish", "addspawn", "setlaps", "togglecollision");
        }
    }

    @Nested
    @DisplayName("End-to-End (E2E) Scenario")
    class EndToEndTests {

        @Test
        @DisplayName("Complete E2E: Track setup via wand/admin commands, player joins, crosses line and finishes")
        void shouldExecuteFullRaceE2EWorkflow() {
            PlayerMock admin = server.addPlayer("AdminDan");
            admin.addAttachment(plugin, "danaevent.boatrace.admin", true);
            admin.addAttachment(plugin, "danaevent.admin.wand", true);

            // 1. Give wand and set pos1 & pos2 for start line
            server.dispatchCommand(admin, "de wand");
            drainMessages(admin);

            plugin.getSelectionManager().setPos1(admin.getUniqueId(), new Location(world, 0, 60, 0));
            plugin.getSelectionManager().setPos2(admin.getUniqueId(), new Location(world, 5, 62, 2));

            // 2. Create track 'gp_e2e'
            server.dispatchCommand(admin, "de br admin track create gp_e2e SPRINT 247");
            assertThat(module.getTrackManager().getTrack("gp_e2e")).isPresent();

            // 3. Define start region from selection
            server.dispatchCommand(admin, "de br admin track gp_e2e setstart");

            // 4. Define finish region
            plugin.getSelectionManager().setPos1(admin.getUniqueId(), new Location(world, 50, 60, 0));
            plugin.getSelectionManager().setPos2(admin.getUniqueId(), new Location(world, 55, 62, 2));
            server.dispatchCommand(admin, "de br admin track gp_e2e setfinish");

            // 5. Add spawn point
            admin.teleport(new Location(world, 1, 61, 1));
            server.dispatchCommand(admin, "de br admin track gp_e2e addspawn");

            Track track = module.getTrackManager().getTrack("gp_e2e").orElseThrow();
            assertThat(track.isReady()).isTrue();

            // 6. Virtual Racer joins the race
            PlayerMock racer = server.addPlayer("SpeedyJoe");
            server.dispatchCommand(racer, "de br join gp_e2e");

            assertThat(module.getRaceManager().isRacing(racer.getUniqueId())).isTrue();
            assertThat(racer.getVehicle()).isInstanceOf(Boat.class);
            Boat raceBoat = (Boat) racer.getVehicle();

            // HUD task runs periodically without exception
            module.getHudTask().run();

            // 7. Racer moves and crosses finish line
            Location fromLoc = new Location(world, 40, 61, 1);
            Location toFinish = new Location(world, 52, 61, 1);

            VehicleMoveEvent moveEvent = new VehicleMoveEvent(raceBoat, fromLoc, toFinish);
            server.getPluginManager().callEvent(moveEvent);

            // 8. Verification: Race completed, player no longer racing, boat removed
            assertThat(module.getRaceManager().isRacing(racer.getUniqueId())).isFalse();
            assertThat(racer.getVehicle()).isNull();

            List<String> racerMsgs = drainMessages(racer);
            assertThat(racerMsgs).anyMatch(m -> m.contains("Course terminée !"));
        }
    }
}
