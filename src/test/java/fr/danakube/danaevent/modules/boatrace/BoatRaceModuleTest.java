package fr.danakube.danaevent.modules.boatrace;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.core.module.ModuleStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BoatRaceModuleTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private BoatRaceModule module;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        Optional<?> opt = plugin.getModuleManager().getModule("boatrace");
        assertThat(opt).isPresent();
        module = (BoatRaceModule) opt.get();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should have correct module metadata and aliases")
    void shouldHaveCorrectMetadata() {
        assertThat(module.getId()).isEqualTo("boatrace");
        assertThat(module.getName()).isEqualTo("Course de Bateaux");
        assertThat(module.getVersion()).isEqualTo("1.0.0");
        assertThat(module.getAliases()).containsExactly("br");
    }

    @Test
    @DisplayName("Should be resolvable in ModuleManager by ID and alias")
    void shouldBeResolvableByIdAndAlias() {
        assertThat(plugin.getModuleManager().getModule("boatrace")).contains(module);
        assertThat(plugin.getModuleManager().getModule("BOATRACE")).contains(module);
        assertThat(plugin.getModuleManager().getModule("br")).contains(module);
        assertThat(plugin.getModuleManager().getModule("BR")).contains(module);

        assertThat(plugin.getModuleManager().getModuleStatus("boatrace")).isEqualTo(ModuleStatus.ENABLED);
        assertThat(plugin.getModuleManager().getModuleStatus("br")).isEqualTo(ModuleStatus.ENABLED);
    }

    @Test
    @DisplayName("Should have all services, listeners, tasks and subcommands initialized on enable")
    void shouldInitializeAllServicesOnEnable() {
        assertThat(module.isEnabled()).isTrue();
        assertThat(module.getDatabase()).isNotNull();
        assertThat(module.getTrackManager()).isNotNull();
        assertThat(module.getCollisionManager()).isNotNull();
        assertThat(module.getRaceManager()).isNotNull();
        assertThat(module.getLeaderboardManager()).isNotNull();
        assertThat(module.getHudTask()).isNotNull();
        assertThat(module.getHudTask().isCancelled()).isFalse();

        assertThat(plugin.getBoatRaceLeaderboardManager()).isSameAs(module.getLeaderboardManager());
        assertThat(module.getBoatSkinManager()).isNotNull();

        List<String> subCommandNames = module.getSubCommands().stream().map(SubCommand::getName).toList();
        assertThat(subCommandNames).containsExactlyInAnyOrder("list", "join", "leave", "top", "boat", "hud", "admin");
    }

    @Test
    @DisplayName("Should cleanly disable, cancel tasks, clean up managers and restore state on disable")
    void shouldCleanlyDisable() {
        assertThat(module.isEnabled()).isTrue();

        plugin.getModuleManager().disableModule("boatrace");

        assertThat(module.isEnabled()).isFalse();
        assertThat(plugin.getModuleManager().getModuleStatus("boatrace")).isEqualTo(ModuleStatus.DISABLED);
        assertThat(module.getHudTask()).isNull();
        assertThat(module.getRaceManager().getActiveSessions()).isEmpty();
        assertThat(plugin.getBoatRaceLeaderboardManager()).isNull();

        // Re-enable to verify clean re-initialization
        plugin.getModuleManager().enableModule("br");
        assertThat(module.isEnabled()).isTrue();
        assertThat(plugin.getModuleManager().getModuleStatus("br")).isEqualTo(ModuleStatus.ENABLED);
        assertThat(module.getHudTask()).isNotNull();
        assertThat(plugin.getBoatRaceLeaderboardManager()).isNotNull();
    }

    @Test
    @DisplayName("Should support onReload by reloading tracks and refreshing cache")
    void shouldHandleReload() {
        module.getTrackManager().createTrack("reload_track", "Reload Track",
            fr.danakube.danaevent.modules.boatrace.model.TrackType.SPRINT,
            fr.danakube.danaevent.modules.boatrace.model.TrackMode.TIME_ATTACK_247);
        module.getTrackManager().saveTracks();

        plugin.getModuleManager().reloadModule("br");

        assertThat(module.isEnabled()).isTrue();
        assertThat(module.getTrackManager().getTrack("reload_track")).isPresent();
    }
}
