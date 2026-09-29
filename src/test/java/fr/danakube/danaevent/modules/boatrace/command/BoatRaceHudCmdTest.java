package fr.danakube.danaevent.modules.boatrace.command;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.boatrace.BoatRaceModule;
import fr.danakube.danaevent.modules.boatrace.model.HudType;
import fr.danakube.danaevent.modules.boatrace.model.Track;
import fr.danakube.danaevent.modules.boatrace.model.TrackMode;
import fr.danakube.danaevent.modules.boatrace.model.TrackType;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BoatRaceHudCmdTest {

    private ServerMock server;
    private WorldMock world;
    private DanaEventPlugin plugin;
    private BoatRaceModule module;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        world = server.addSimpleWorld("hud_cmd_world");
        module = plugin.getModuleManager().getModule("boatrace", BoatRaceModule.class).orElseThrow();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private List<String> drainMessages(PlayerMock player) {
        List<String> list = new ArrayList<>();
        var plain = PlainTextComponentSerializer.plainText();
        while (true) {
            var c = player.nextComponentMessage();
            if (c == null) break;
            list.add(plain.serialize(c));
        }
        return list;
    }

    @Test
    @DisplayName("/de br hud should display current HUD type when invoked with no args")
    void shouldDisplayCurrentHudType() {
        PlayerMock player = server.addPlayer("HudTester");
        drainMessages(player);

        server.dispatchCommand(player, "de br hud");

        List<String> msgs = drainMessages(player);
        assertThat(msgs).anyMatch(m -> m.contains("Votre affichage du chrono actuel"));
    }

    @Test
    @DisplayName("/de br hud <type> should update preference and notify player")
    void shouldUpdateHudPreference() {
        PlayerMock player = server.addPlayer("HudTester2");
        drainMessages(player);

        server.dispatchCommand(player, "de br hud actionbar");
        assertThat(module.getRaceManager().getPlayerHudPreference(player.getUniqueId())).isEqualTo(HudType.ACTION_BAR);

        List<String> msgs = drainMessages(player);
        assertThat(msgs).anyMatch(m -> m.contains("Action Bar"));

        server.dispatchCommand(player, "de br hud bossbar");
        assertThat(module.getRaceManager().getPlayerHudPreference(player.getUniqueId())).isEqualTo(HudType.BOSS_BAR);

        server.dispatchCommand(player, "de br hud both");
        assertThat(module.getRaceManager().getPlayerHudPreference(player.getUniqueId())).isEqualTo(HudType.BOTH);
    }

    @Test
    @DisplayName("/de br hud invalid should reject with error message")
    void shouldRejectInvalidHudType() {
        PlayerMock player = server.addPlayer("HudTester3");
        drainMessages(player);

        server.dispatchCommand(player, "de br hud unknown");

        List<String> msgs = drainMessages(player);
        assertThat(msgs).anyMatch(m -> m.contains("Mode d'affichage invalide"));
    }

    @Test
    @DisplayName("Changing HUD preference during active race immediately updates race session")
    void shouldUpdateActiveRaceSessionHud() {
        PlayerMock player = server.addPlayer("RacerHud");

        Track track = new Track("hud_race_track", "HUD Track", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
        track.setStartRegion(new CuboidRegion(new Location(world, 0, 60, 0), new Location(world, 2, 62, 2)));
        track.setFinishRegion(new CuboidRegion(new Location(world, 20, 60, 0), new Location(world, 22, 62, 2)));
        track.addSpawnPoint(new Location(world, 1, 61, 1));
        module.getTrackManager().registerTrack(track);

        module.getRaceManager().startRace(player, track);
        var session = module.getRaceManager().getSession(player.getUniqueId()).orElseThrow();
        assertThat(session.getHudType()).isEqualTo(HudType.BOSS_BAR);

        // Switch to actionbar
        server.dispatchCommand(player, "de br hud actionbar");
        assertThat(session.getHudType()).isEqualTo(HudType.ACTION_BAR);
    }
}
