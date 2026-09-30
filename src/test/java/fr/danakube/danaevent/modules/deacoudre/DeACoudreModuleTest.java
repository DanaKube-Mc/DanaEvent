package fr.danakube.danaevent.modules.deacoudre;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.module.DanaModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DeACoudreModuleTest {

    private ServerMock server;
    private DanaEventPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should be registered, enabled, and possess valid subcommands and lifecycle")
    void testModuleLifecycle() {
        Optional<DanaModule> moduleOpt = plugin.getModuleManager().getModule("deacoudre");
        assertThat(moduleOpt).isPresent();

        DanaModule module = moduleOpt.get();
        assertThat(module.getName()).isEqualTo("Dé à Coudre");
        assertThat(module.getAliases()).contains("dac");
        assertThat(module.isEnabled()).isTrue();
        assertThat(module.getSubCommands()).isNotEmpty();

        // Reload module
        module.onReload();
        assertThat(module.isEnabled()).isTrue();

        // Disable module
        plugin.getModuleManager().disableModule("deacoudre");
        assertThat(module.isEnabled()).isFalse();
    }
}
