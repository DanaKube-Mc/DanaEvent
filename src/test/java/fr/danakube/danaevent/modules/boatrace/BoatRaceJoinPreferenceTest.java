package fr.danakube.danaevent.modules.boatrace;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.boatrace.gui.BoatSelectionGui;
import fr.danakube.danaevent.modules.boatrace.model.RaceSession;
import fr.danakube.danaevent.modules.boatrace.model.Track;
import fr.danakube.danaevent.modules.boatrace.model.TrackMode;
import fr.danakube.danaevent.modules.boatrace.model.TrackType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BoatRaceJoinPreferenceTest {

    private ServerMock server;
    private WorldMock world;
    private DanaEventPlugin plugin;
    private BoatRaceModule module;
    private Track track;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        world = server.addSimpleWorld("race_world");

        Optional<?> opt = plugin.getModuleManager().getModule("boatrace");
        assertThat(opt).isPresent();
        module = (BoatRaceModule) opt.get();

        // Setup a ready track
        track = module.getTrackManager().createTrack("speedway", "Speedway", TrackType.CIRCUIT_LAPS, TrackMode.TIME_ATTACK_247);
        track.addSpawnPoint(new Location(world, 10, 64, 10));
        track.setStartRegion(new CuboidRegion(world.getName(), 9, 63, 9, 11, 66, 11));
        track.setFinishRegion(new CuboidRegion(world.getName(), 19, 63, 19, 21, 66, 21));
        track.setLaps(2);
        track.setBoatMaterial(Material.OAK_BOAT);
        assertThat(track.isReady()).isTrue();
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

    @Test
    @DisplayName("Option B - First join opens boat selection GUI, selecting boat starts race with chosen boat")
    void firstJoinOpensGuiAndStartsRaceOnSelection() {
        PlayerMock player = server.addPlayer("Rookie");
        assertThat(module.getBoatSkinManager().hasPreference(player.getUniqueId())).isFalse();

        // 1. Player joins track for the first time
        player.performCommand("danaevent br join speedway");

        // Player should NOT yet be racing
        assertThat(module.getRaceManager().isRacing(player.getUniqueId())).isFalse();

        // GUI should be opened
        assertThat(player.getOpenInventory().getTopInventory()).isNotNull();
        assertThat(player.getOpenInventory().getTopInventory().getSize()).isEqualTo(36);

        // 2. Player clicks SPRUCE_BOAT (slot 12 in default layout: oak=11, spruce=12)
        var clickEvent = new InventoryClickEvent(
            player.getOpenInventory(),
            InventoryType.SlotType.CONTAINER,
            12,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL
        );
        plugin.getServer().getPluginManager().callEvent(clickEvent);

        // Preference must now be stored
        assertThat(module.getBoatSkinManager().hasPreference(player.getUniqueId())).isTrue();
        assertThat(module.getBoatSkinManager().getPreference(player.getUniqueId())).isEqualTo(Material.SPRUCE_BOAT);

        // Race must have started automatically
        assertThat(module.getRaceManager().isRacing(player.getUniqueId())).isTrue();

        Optional<RaceSession> sessionOpt = module.getRaceManager().getSession(player.getUniqueId());
        assertThat(sessionOpt).isPresent();
        assertThat(sessionOpt.get().getBoat()).isNotNull();
    }

    @Test
    @DisplayName("Option B - Subsequent join starts race immediately with memorized preferred boat")
    void subsequentJoinStartsRaceImmediatelyWithPreferredBoat() {
        PlayerMock player = server.addPlayer("Veteran");

        // Memorize preference beforehand
        module.getBoatSkinManager().setPreference(player.getUniqueId(), Material.SPRUCE_BOAT);
        assertThat(module.getBoatSkinManager().hasPreference(player.getUniqueId())).isTrue();

        // Player joins track
        player.performCommand("danaevent br join speedway");

        // Race should start immediately without prompting GUI
        assertThat(module.getRaceManager().isRacing(player.getUniqueId())).isTrue();
        Optional<RaceSession> sessionOpt = module.getRaceManager().getSession(player.getUniqueId());
        assertThat(sessionOpt).isPresent();
        assertThat(player.getVehicle()).isNotNull();
    }

    @Test
    @DisplayName("/de br boat opens cosmetic dressing room to change preferred boat")
    void boatCommandOpensSelectionGuiAndUpdatesPreference() {
        PlayerMock player = server.addPlayer("FashionRacer");
        module.getBoatSkinManager().setPreference(player.getUniqueId(), Material.OAK_BOAT);

        // Player opens boat menu via /de br boat
        player.performCommand("danaevent br boat");

        assertThat(player.getOpenInventory().getTopInventory()).isNotNull();
        assertThat(player.getOpenInventory().getTopInventory().getSize()).isEqualTo(BoatSelectionGui.SIZE);

        // Click SPRUCE_BOAT (slot 12)
        var clickEvent = new InventoryClickEvent(
            player.getOpenInventory(),
            InventoryType.SlotType.CONTAINER,
            12,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL
        );
        plugin.getServer().getPluginManager().callEvent(clickEvent);

        // Preference updated
        assertThat(module.getBoatSkinManager().getPreference(player.getUniqueId())).isEqualTo(Material.SPRUCE_BOAT);
        List<String> messages = drainMessages(player);
        assertThat(messages).anyMatch(m -> m.contains("sélectionné") || m.contains("Bateau en Sapin"));
    }

    @Test
    @DisplayName("/de br boat while racing is rejected")
    void boatCommandWhileRacingIsRejected() {
        PlayerMock player = server.addPlayer("Speedy");
        module.getBoatSkinManager().setPreference(player.getUniqueId(), Material.OAK_BOAT);

        // Start race
        boolean started = module.getRaceManager().startRace(player, track);
        assertThat(started).isTrue();
        assertThat(module.getRaceManager().isRacing(player.getUniqueId())).isTrue();

        // Try /de br boat
        player.performCommand("danaevent br boat");

        List<String> messages = drainMessages(player);
        assertThat(messages).anyMatch(m -> m.contains("déjà en pleine course"));
    }
}
