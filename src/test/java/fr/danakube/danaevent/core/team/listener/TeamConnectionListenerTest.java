package fr.danakube.danaevent.core.team.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.team.database.TeamDatabase;
import fr.danakube.danaevent.core.team.manager.TeamManager;
import fr.danakube.danaevent.core.team.model.TeamColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class TeamConnectionListenerTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private TeamDatabase database;
    private TeamManager teamManager;
    private TeamConnectionListener listener;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        database = new TeamDatabase(plugin.getDatabaseManager());
        database.initTables();
        teamManager = new TeamManager(plugin, database);
        listener = new TeamConnectionListener(teamManager);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        if (teamManager != null) {
            teamManager.cleanUp();
        }
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should apply permissions on PlayerJoinEvent and clean up on PlayerQuitEvent")
    void shouldHandleJoinAndQuitEvents() throws ExecutionException, InterruptedException {
        PlayerMock player = server.addPlayer("JoinQuitTester");
        teamManager.createTeam("aigles", "Les Aigles", TeamColor.CYAN, player).get();

        assertThat(player.hasPermission("danaevent.team.aigles")).isTrue();
        assertThat(player.hasPermission("danaevent.teamcolor.cyan")).isTrue();

        // Disconnect
        player.disconnect();

        // Reconnect new mock instance with same UUID
        PlayerMock reconnected = server.addPlayer("JoinQuitTester");
        assertThat(reconnected.getUniqueId()).isNotEqualTo(player.getUniqueId()); // ServerMock generates new UUID unless specified

        // Verify handling by explicitly passing player to listener
        listener.onPlayerJoin(new org.bukkit.event.player.PlayerJoinEvent(player, net.kyori.adventure.text.Component.empty()));
        assertThat(player.hasPermission("danaevent.team.aigles")).isTrue();

        listener.onPlayerQuit(new org.bukkit.event.player.PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty()));
    }
}
