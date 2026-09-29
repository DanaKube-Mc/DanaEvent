package fr.danakube.danaevent.core.command;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.command.ConsoleCommandSenderMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.boatrace.BoatRaceModule;
import fr.danakube.danaevent.modules.boatrace.model.Track;
import fr.danakube.danaevent.modules.boatrace.model.TrackMode;
import fr.danakube.danaevent.modules.boatrace.model.TrackType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveCommandTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private WorldMock world;
    private BoatRaceModule boatRaceModule;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        world = server.addSimpleWorld("leave_world");
        boatRaceModule = plugin.getModuleManager().getModule("boatrace", BoatRaceModule.class).orElseThrow();
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

    @Test
    @DisplayName("/de leave when not in event should send not-in-event message")
    void shouldNotifyWhenNotInEvent() {
        PlayerMock player = server.addPlayer("FreePlayer");
        server.dispatchCommand(player, "de leave");

        List<String> msgs = drainMessages(player);
        assertThat(msgs).anyMatch(m -> m.contains("ne participez actuellement à aucun événement"));
    }

    @Test
    @DisplayName("/leave from console should fail with player-only message")
    void shouldFailFromConsole() {
        ConsoleCommandSenderMock console = server.getConsoleSender();
        server.dispatchCommand(console, "leave");

        assertThat(console.nextMessage()).contains("ne peut être exécutée que par un joueur");
    }

    @Test
    @DisplayName("/leave during active race should cancel race and restore player inventory and location")
    void shouldLeaveActiveRaceViaRootCommand() {
        PlayerMock player = server.addPlayer("Racer");
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 3));
        Location originalLoc = new Location(world, 100, 64, 100);
        player.teleport(originalLoc);

        Track track = new Track("leave_track", "Leave Track", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
        track.setStartRegion(new CuboidRegion(new Location(world, 0, 60, 0), new Location(world, 2, 62, 2)));
        track.setFinishRegion(new CuboidRegion(new Location(world, 20, 60, 0), new Location(world, 22, 62, 2)));
        track.addSpawnPoint(new Location(world, 1, 61, 1));
        boatRaceModule.getTrackManager().registerTrack(track);

        boatRaceModule.getRaceManager().startRace(player, track);
        assertThat(boatRaceModule.getRaceManager().isRacing(player.getUniqueId())).isTrue();
        Boat boat = (Boat) player.getVehicle();
        assertThat(boat).isNotNull();

        // Drain messages
        drainMessages(player);

        // Execute /leave
        server.dispatchCommand(player, "leave");

        assertThat(boatRaceModule.getRaceManager().isRacing(player.getUniqueId())).isFalse();
        assertThat(boat.isValid()).isFalse();
        assertThat(player.getVehicle()).isNull();
        assertThat(player.getInventory().contains(Material.DIAMOND)).isTrue();
        assertThat(player.getLocation().getBlockX()).isEqualTo(100);
        assertThat(player.getLocation().getBlockZ()).isEqualTo(100);

        List<String> msgs = drainMessages(player);
        assertThat(msgs).anyMatch(m -> m.contains("Course annulée : Abandon"));
    }

    @Test
    @DisplayName("/de leave during active race should also cancel race and restore player")
    void shouldLeaveActiveRaceViaDeSubCommand() {
        PlayerMock player = server.addPlayer("Racer2");
        player.getInventory().addItem(new ItemStack(Material.EMERALD, 5));

        Track track = new Track("leave_track2", "Leave Track 2", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
        track.setStartRegion(new CuboidRegion(new Location(world, 0, 60, 0), new Location(world, 2, 62, 2)));
        track.setFinishRegion(new CuboidRegion(new Location(world, 20, 60, 0), new Location(world, 22, 62, 2)));
        track.addSpawnPoint(new Location(world, 1, 61, 1));
        boatRaceModule.getTrackManager().registerTrack(track);

        boatRaceModule.getRaceManager().startRace(player, track);
        assertThat(boatRaceModule.getRaceManager().isRacing(player.getUniqueId())).isTrue();

        // Execute /de leave
        server.dispatchCommand(player, "de leave");

        assertThat(boatRaceModule.getRaceManager().isRacing(player.getUniqueId())).isFalse();
        assertThat(player.getInventory().contains(Material.EMERALD)).isTrue();
    }
}
