package fr.danakube.danaevent.core.team.manager;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TeamScoreboardManagerTest {

    private ServerMock server;
    private Scoreboard scoreboard;
    private TeamScoreboardManager scoreboardManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        scoreboard = server.getScoreboardManager().getMainScoreboard();
        scoreboardManager = new TeamScoreboardManager(scoreboard);
    }

    @AfterEach
    void tearDown() {
        if (scoreboardManager != null) {
            scoreboardManager.cleanUp();
        }
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should register a Bukkit team with correct color, name and friendly settings")
    void shouldRegisterBukkitTeam() {
        UUID leaderUuid = UUID.randomUUID();
        DanaTeam team = new DanaTeam("alpha", "Alpha Team", TeamColor.RED, leaderUuid);

        Team bukkitTeam = scoreboardManager.registerTeam(team);

        assertThat(bukkitTeam).isNotNull();
        assertThat(bukkitTeam.getName()).isEqualTo("det_alpha");
        assertThat(bukkitTeam.color()).isEqualTo(NamedTextColor.RED);
        assertThat(bukkitTeam.allowFriendlyFire()).isFalse();
        assertThat(bukkitTeam.canSeeFriendlyInvisibles()).isTrue();
    }

    @Test
    @DisplayName("Should update Bukkit team color when team color changes")
    void shouldUpdateBukkitTeamColor() {
        UUID leaderUuid = UUID.randomUUID();
        DanaTeam team = new DanaTeam("alpha", "Alpha Team", TeamColor.RED, leaderUuid);
        scoreboardManager.registerTeam(team);

        team.setColor(TeamColor.BLUE);
        scoreboardManager.updateTeamColor(team);

        Team bukkitTeam = scoreboard.getTeam("det_alpha");
        assertThat(bukkitTeam).isNotNull();
        assertThat(bukkitTeam.color()).isEqualTo(NamedTextColor.BLUE);
    }

    @Test
    @DisplayName("Should add and remove player from Bukkit team")
    void shouldAddAndRemovePlayer() {
        PlayerMock player = server.addPlayer("GamerOne");
        DanaTeam team = new DanaTeam("blue_team", "Blue Team", TeamColor.BLUE, player.getUniqueId());

        scoreboardManager.addPlayer(team, player);
        Team bukkitTeam = scoreboard.getTeam("det_blue_team");
        assertThat(bukkitTeam).isNotNull();
        assertThat(bukkitTeam.hasPlayer(player)).isTrue();

        scoreboardManager.removePlayer(player);
        assertThat(bukkitTeam.hasPlayer(player)).isFalse();
    }

    @Test
    @DisplayName("Should toggle player glowing state correctly")
    void shouldTogglePlayerGlowing() {
        PlayerMock player = server.addPlayer("GlowingPlayer");

        assertThat(scoreboardManager.isGlowing(player)).isFalse();
        scoreboardManager.setGlowing(player, true);
        assertThat(scoreboardManager.isGlowing(player)).isTrue();
        scoreboardManager.setGlowing(player, false);
        assertThat(scoreboardManager.isGlowing(player)).isFalse();
    }

    @Test
    @DisplayName("Should unregister Bukkit team cleanly")
    void shouldUnregisterTeam() {
        DanaTeam team = new DanaTeam("green_team", "Green Team", TeamColor.GREEN, UUID.randomUUID());
        scoreboardManager.registerTeam(team);
        assertThat(scoreboard.getTeam("det_green_team")).isNotNull();

        scoreboardManager.unregisterTeam("green_team");
        assertThat(scoreboard.getTeam("det_green_team")).isNull();
    }

    @Test
    @DisplayName("Should clean up all registered teams on cleanUp()")
    void shouldCleanUpAllTeams() {
        DanaTeam team1 = new DanaTeam("team1", "Team 1", TeamColor.YELLOW, UUID.randomUUID());
        DanaTeam team2 = new DanaTeam("team2", "Team 2", TeamColor.PURPLE, UUID.randomUUID());

        scoreboardManager.registerTeam(team1);
        scoreboardManager.registerTeam(team2);

        assertThat(scoreboard.getTeam("det_team1")).isNotNull();
        assertThat(scoreboard.getTeam("det_team2")).isNotNull();

        scoreboardManager.cleanUp();

        assertThat(scoreboard.getTeam("det_team1")).isNull();
        assertThat(scoreboard.getTeam("det_team2")).isNull();
    }
}
