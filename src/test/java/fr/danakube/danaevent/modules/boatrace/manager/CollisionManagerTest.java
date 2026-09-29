package fr.danakube.danaevent.modules.boatrace.manager;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CollisionManagerTest {

    private ServerMock server;
    private Scoreboard scoreboard;
    private CollisionManager collisionManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        scoreboard = server.getScoreboardManager().getMainScoreboard();
        collisionManager = new CollisionManager(scoreboard);
    }

    @AfterEach
    void tearDown() {
        if (collisionManager != null) {
            collisionManager.cleanUp();
        }
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should initialize collision team with COLLISION_RULE set to NEVER")
    void shouldInitializeCollisionTeamWithNeverRule() {
        Team team = scoreboard.getTeam(CollisionManager.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.getOption(Team.Option.COLLISION_RULE)).isEqualTo(Team.OptionStatus.NEVER);
    }

    @Test
    @DisplayName("Should add, check and remove players from the collision team")
    void shouldAddAndRemovePlayers() {
        PlayerMock player = server.addPlayer("RacerOne");

        assertThat(collisionManager.hasPlayer(player)).isFalse();

        collisionManager.addPlayer(player);
        assertThat(collisionManager.hasPlayer(player)).isTrue();

        collisionManager.removePlayer(player);
        assertThat(collisionManager.hasPlayer(player)).isFalse();
    }

    @Test
    @DisplayName("Should clean up team entries and unregister team on cleanUp()")
    void shouldCleanUpTeam() {
        PlayerMock player1 = server.addPlayer("RacerOne");
        PlayerMock player2 = server.addPlayer("RacerTwo");

        collisionManager.addPlayer(player1);
        collisionManager.addPlayer(player2);

        collisionManager.cleanUp();

        assertThat(scoreboard.getTeam(CollisionManager.TEAM_NAME)).isNull();
    }
}
