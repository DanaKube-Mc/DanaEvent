package fr.danakube.danaevent;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import fr.danakube.danaevent.core.database.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DanaEventPluginTest {

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
    @DisplayName("Plugin should load and enable properly under MockBukkit with DatabaseManager active")
    void shouldLoadAndEnablePlugin() {
        assertThat(plugin).isNotNull();
        assertThat(plugin.isEnabled()).isTrue();
        assertThat(DanaEventPlugin.getInstance()).isSameAs(plugin);

        DatabaseManager dbManager = plugin.getDatabaseManager();
        assertThat(dbManager).isNotNull();
        assertThat(dbManager.isRunning()).isTrue();

        assertThat(plugin.getMessageManager()).isNotNull();
        assertThat(plugin.getPlayerStateManager()).isNotNull();
        assertThat(plugin.getSelectionManager()).isNotNull();
        assertThat(plugin.getModuleManager()).isNotNull();
        assertThat(plugin.getCommandManager()).isNotNull();
    }

    @Test
    @DisplayName("Plugin should cleanly shut down DatabaseManager and modules when disabled")
    void shouldShutdownDatabaseManagerOnDisable() {
        DatabaseManager dbManager = plugin.getDatabaseManager();
        assertThat(dbManager).isNotNull();
        assertThat(dbManager.isRunning()).isTrue();

        MockBukkit.unmock();

        assertThat(dbManager.isRunning()).isFalse();
        assertThat(dbManager.getDataSource().isClosed()).isTrue();
        assertThat(DanaEventPlugin.getInstance()).isNull();
        assertThat(plugin.getMessageManager()).isNull();
        assertThat(plugin.getPlayerStateManager()).isNull();
        assertThat(plugin.getSelectionManager()).isNull();
        assertThat(plugin.getModuleManager()).isNull();
        assertThat(plugin.getCommandManager()).isNull();
    }
}
