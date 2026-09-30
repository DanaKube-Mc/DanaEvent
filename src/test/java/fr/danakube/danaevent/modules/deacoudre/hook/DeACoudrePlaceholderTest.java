package fr.danakube.danaevent.modules.deacoudre.hook;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.hook.DanaEventPlaceholderExpansion;
import fr.danakube.danaevent.modules.deacoudre.DeACoudreModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeACoudrePlaceholderTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private DeACoudreModule module;
    private DanaEventPlaceholderExpansion expansion;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        module = (DeACoudreModule) plugin.getModuleManager().getModule("deacoudre").orElseThrow();
        expansion = new DanaEventPlaceholderExpansion(plugin);
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should resolve DAC placeholders with fallback and valid record data")
    void testPlaceholderResolution() {
        PlayerMock player = server.addPlayer("PapiPlayer");

        // Player perfects fallback
        String perfects = expansion.onRequest(player, "dac_player_perfects");
        assertThat(perfects).isEqualTo("0");

        // Top1 fallback
        String top1Name = expansion.onRequest(player, "dac_unknown_top1_name");
        assertThat(top1Name).isEqualTo("N/A");

        String top1Wins = expansion.onRequest(player, "dac_unknown_top1_wins");
        assertThat(top1Wins).isEqualTo("0");
    }
}
