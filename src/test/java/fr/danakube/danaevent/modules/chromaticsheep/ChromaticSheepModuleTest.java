package fr.danakube.danaevent.modules.chromaticsheep;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChromaticSheepModuleTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private ChromaticSheepModule module;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        module = plugin.getChromaticSheepModule();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("ChromaticSheepModule should be registered, enabled, and have all required subcommands")
    void shouldRegisterAndEnableModule() {
        assertThat(module).isNotNull();
        assertThat(module.isEnabled()).isTrue();
        assertThat(module.getId()).isEqualTo("chromaticsheep");
        assertThat(module.getAliases()).contains("mc");

        List<String> subCmdNames = module.getSubCommands().stream()
            .map(cmd -> cmd.getName().toLowerCase())
            .toList();

        assertThat(subCmdNames).contains("list", "join", "leave", "top", "admin");
    }

    @Test
    @DisplayName("Should cleanly disable, reload, and re-enable module without exceptions")
    void shouldHandleLifecycleCleanly() {
        module.onDisable();
        assertThat(module.isEnabled()).isFalse();

        module.onEnable();
        assertThat(module.isEnabled()).isTrue();

        module.onReload();
        assertThat(module.isEnabled()).isTrue();
    }
}
