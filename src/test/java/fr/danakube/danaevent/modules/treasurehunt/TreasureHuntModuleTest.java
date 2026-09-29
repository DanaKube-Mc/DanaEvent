package fr.danakube.danaevent.modules.treasurehunt;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreasureHuntModuleTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private TreasureHuntModule module;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        module = plugin.getTreasureHuntModule();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("TreasureHuntModule should be registered, enabled, and have all required subcommands")
    void shouldRegisterAndEnableModule() {
        assertThat(module).isNotNull();
        assertThat(module.isEnabled()).isTrue();
        assertThat(module.getId()).isEqualTo("treasurehunt");
        assertThat(module.getAliases()).contains("hunt", "th");

        List<String> subCmdNames = module.getSubCommands().stream()
            .map(cmd -> cmd.getName().toLowerCase())
            .toList();

        assertThat(subCmdNames).contains("list", "join", "leave", "journal", "top", "admin");
    }

    @Test
    @DisplayName("Should cleanly disable, reload, and re-enable module without exceptions")
    void shouldHandleLifecycleCleanly() {
        module.onDisable();
        assertThat(module.isEnabled()).isFalse();
        assertThat(module.getHudTask()).isNull();
        assertThat(module.getParticleTask()).isNull();

        module.onEnable();
        assertThat(module.isEnabled()).isTrue();
        assertThat(module.getHudTask()).isNotNull();
        assertThat(module.getParticleTask()).isNotNull();

        module.onReload();
    }
}
