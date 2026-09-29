package fr.danakube.danaevent.core.hook;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HookManagerTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private HookManager hookManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        hookManager = plugin.getHookManager();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("HookManager should detect third-party plugins as absent in default MockBukkit environment")
    void shouldDetectPluginsAsAbsentByDefault() {
        assertThat(hookManager).isNotNull();
        assertThat(hookManager.isPlaceholderApiAvailable()).isFalse();
        assertThat(hookManager.isDecentHologramsAvailable()).isFalse();
        assertThat(hookManager.getPlaceholderApiHook()).isNotNull();
        assertThat(hookManager.getDecentHologramsHook()).isNotNull();
        assertThat(hookManager.getDecentHologramsHook().isAvailable()).isFalse();
        assertThat(hookManager.getDecentHologramsHook().getPlugin()).isSameAs(plugin);
    }

    @Test
    @DisplayName("PlaceholderAPIHook should transparently return unmodified text without crash when PAPI is absent")
    void shouldFallbackTransparentlyWhenPapiIsAbsent() {
        PlayerMock player = server.addPlayer("Steve");
        String text = "Hello %player_name%, active modules: %danaevent_active_modules%";

        String result = hookManager.parsePlaceholders(player, text);
        assertThat(result).isEqualTo(text);

        // Test with null player and null text
        assertThat(hookManager.parsePlaceholders(null, text)).isEqualTo(text);
        assertThat(hookManager.parsePlaceholders(player, null)).isNull();
        assertThat(hookManager.parsePlaceholders(null, null)).isNull();
    }

    @Test
    @DisplayName("HookManager should accurately report custom availability states")
    void shouldReportCustomAvailability() {
        HookManager customHookManager = new HookManager(plugin, false, true);

        assertThat(customHookManager.isPlaceholderApiAvailable()).isFalse();
        assertThat(customHookManager.isDecentHologramsAvailable()).isTrue();
        assertThat(customHookManager.getDecentHologramsHook().isAvailable()).isTrue();
        assertThat(customHookManager.getPlaceholderApiHook().isAvailable()).isFalse();

        // Fallback should still work cleanly
        PlayerMock player = server.addPlayer("Alex");
        String result = customHookManager.parsePlaceholders(player, "Test %danaevent_version%");
        assertThat(result).isEqualTo("Test %danaevent_version%");
    }

    @Test
    @DisplayName("HookManager should clean up properly without error")
    void shouldCleanUpWithoutError() {
        hookManager.cleanUp();
        // Subsequent calls should still not crash
        assertThat(hookManager.parsePlaceholders(null, "Test")).isEqualTo("Test");
    }
}
