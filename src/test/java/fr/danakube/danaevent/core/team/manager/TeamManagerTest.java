package fr.danakube.danaevent.core.team.manager;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.team.database.TeamDatabase;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import fr.danakube.danaevent.core.team.model.TeamInvite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamManagerTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private TeamDatabase database;
    private TeamManager teamManager;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        database = new TeamDatabase(plugin.getDatabaseManager());
        database.initTables();
        teamManager = new TeamManager(plugin, database);
        teamManager.loadAllTeams().get();
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
    @DisplayName("Should create team, assign leader, reserve color and attach dynamic permissions")
    void shouldCreateTeamAndAssignPermissions() throws ExecutionException, InterruptedException {
        PlayerMock leader = server.addPlayer("LeaderOne");

        DanaTeam team = teamManager.createTeam("titans", "Les Titans", TeamColor.BLUE, leader).get();

        assertThat(team).isNotNull();
        assertThat(team.getId()).isEqualTo("titans");
        assertThat(team.getDisplayName()).isEqualTo("Les Titans");
        assertThat(team.getColor()).isEqualTo(TeamColor.BLUE);
        assertThat(team.getLeaderUuid()).isEqualTo(leader.getUniqueId());

        // Cache checks
        assertThat(teamManager.hasTeam(leader.getUniqueId())).isTrue();
        assertThat(teamManager.getPlayerTeam(leader.getUniqueId())).contains(team);
        assertThat(teamManager.getTeam("titans")).contains(team);
        assertThat(teamManager.isColorAvailable(TeamColor.BLUE)).isFalse();
        assertThat(teamManager.getTeamByColor(TeamColor.BLUE)).contains(team);

        // Dynamic permissions check
        assertThat(leader.hasPermission("danaevent.team.titans")).isTrue();
        assertThat(leader.hasPermission("danaevent.teamcolor.blue")).isTrue();
    }

    @Test
    @DisplayName("Should reject team creation if player is already in a team or ID is invalid")
    void shouldRejectInvalidCreation() throws ExecutionException, InterruptedException {
        PlayerMock leader = server.addPlayer("LeaderTwo");
        teamManager.createTeam("eclairs", "Les Éclairs", TeamColor.YELLOW, leader).get();

        // Already in team
        assertThatThrownBy(() -> teamManager.createTeam("eclairs2", "Éclairs 2", TeamColor.GREEN, leader))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already in a team");

        PlayerMock other = server.addPlayer("OtherLeader");
        // Color already taken
        assertThatThrownBy(() -> teamManager.createTeam("autre_equipe", "Autre", TeamColor.YELLOW, other))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Color already in use");

        // Invalid slug ID
        assertThatThrownBy(() -> teamManager.createTeam("NO", "Invalid", TeamColor.RED, other))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should change team color and update permissions for members")
    void shouldChangeTeamColor() throws ExecutionException, InterruptedException {
        PlayerMock leader = server.addPlayer("ColorLeader");
        DanaTeam team = teamManager.createTeam("dragons", "Les Dragons", TeamColor.RED, leader).get();

        assertThat(leader.hasPermission("danaevent.teamcolor.red")).isTrue();

        boolean changed = teamManager.changeTeamColor("dragons", TeamColor.PURPLE).get();
        assertThat(changed).isTrue();
        assertThat(team.getColor()).isEqualTo(TeamColor.PURPLE);

        // Old color freed, new color taken
        assertThat(teamManager.isColorAvailable(TeamColor.RED)).isTrue();
        assertThat(teamManager.isColorAvailable(TeamColor.PURPLE)).isFalse();

        // Permissions updated
        assertThat(leader.hasPermission("danaevent.teamcolor.purple")).isTrue();
    }

    @Test
    @DisplayName("Should send invite, accept invite and update permissions")
    void shouldInviteAndAccept() throws ExecutionException, InterruptedException {
        PlayerMock leader = server.addPlayer("Captain");
        PlayerMock recruit = server.addPlayer("Recruit");

        DanaTeam team = teamManager.createTeam("phoenix", "Les Phoenix", TeamColor.ORANGE, leader).get();

        // Invite recruit
        boolean invited = teamManager.invitePlayer("phoenix", leader.getUniqueId(), recruit);
        assertThat(invited).isTrue();

        List<TeamInvite> pending = teamManager.getPendingInvites(recruit.getUniqueId());
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).teamId()).isEqualTo("phoenix");

        // Accept invite
        boolean accepted = teamManager.acceptInvite(recruit, "phoenix").get();
        assertThat(accepted).isTrue();

        assertThat(team.hasMember(recruit.getUniqueId())).isTrue();
        assertThat(teamManager.hasTeam(recruit.getUniqueId())).isTrue();
        assertThat(recruit.hasPermission("danaevent.team.phoenix")).isTrue();
        assertThat(recruit.hasPermission("danaevent.teamcolor.orange")).isTrue();

        // Invite is consumed
        assertThat(teamManager.getPendingInvites(recruit.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("Should decline invite properly")
    void shouldDeclineInvite() throws ExecutionException, InterruptedException {
        PlayerMock leader = server.addPlayer("LeaderDec");
        PlayerMock recruit = server.addPlayer("RecruitDec");

        teamManager.createTeam("viperes", "Les Vipères", TeamColor.GREEN, leader).get();
        teamManager.invitePlayer("viperes", leader.getUniqueId(), recruit);

        boolean declined = teamManager.declineInvite(recruit.getUniqueId(), "viperes");
        assertThat(declined).isTrue();
        assertThat(teamManager.getPendingInvites(recruit.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("Leader can kick member, removing permissions and memberships")
    void shouldKickMember() throws ExecutionException, InterruptedException {
        PlayerMock leader = server.addPlayer("Boss");
        PlayerMock member = server.addPlayer("Minion");

        DanaTeam team = teamManager.createTeam("pirates", "Les Pirates", TeamColor.BLACK, leader).get();
        teamManager.invitePlayer("pirates", leader.getUniqueId(), member);
        teamManager.acceptInvite(member, "pirates").get();

        assertThat(team.hasMember(member.getUniqueId())).isTrue();
        assertThat(member.hasPermission("danaevent.team.pirates")).isTrue();

        // Kick minion
        teamManager.kickMember("pirates", leader.getUniqueId(), member.getUniqueId()).get();

        assertThat(team.hasMember(member.getUniqueId())).isFalse();
        assertThat(teamManager.hasTeam(member.getUniqueId())).isFalse();
        assertThat(member.hasPermission("danaevent.team.pirates")).isFalse();
    }

    @Test
    @DisplayName("Member leaving team updates memberships, while leader leaving disbands team")
    void shouldHandleLeaveTeam() throws ExecutionException, InterruptedException {
        PlayerMock leader = server.addPlayer("Admiral");
        PlayerMock member = server.addPlayer("Sailor");

        DanaTeam team = teamManager.createTeam("corsaires", "Les Corsaires", TeamColor.CYAN, leader).get();
        teamManager.invitePlayer("corsaires", leader.getUniqueId(), member);
        teamManager.acceptInvite(member, "corsaires").get();

        // 1. Member leaves
        teamManager.leaveTeam(member).get();
        assertThat(team.hasMember(member.getUniqueId())).isFalse();
        assertThat(teamManager.hasTeam(member.getUniqueId())).isFalse();
        assertThat(member.hasPermission("danaevent.team.corsaires")).isFalse();
        assertThat(teamManager.getTeam("corsaires")).isPresent();

        // 2. Leader leaves -> Disband
        teamManager.leaveTeam(leader).get();
        assertThat(teamManager.getTeam("corsaires")).isEmpty();
        assertThat(teamManager.isColorAvailable(TeamColor.CYAN)).isTrue();
        assertThat(leader.hasPermission("danaevent.team.corsaires")).isFalse();
    }

    @Test
    @DisplayName("Disband team should remove permissions, delete from DB and free color")
    void shouldDisbandTeam() throws ExecutionException, InterruptedException {
        PlayerMock leader = server.addPlayer("King");
        teamManager.createTeam("royaume", "Le Royaume", TeamColor.LIME, leader).get();

        assertThat(teamManager.isColorAvailable(TeamColor.LIME)).isFalse();
        assertThat(leader.hasPermission("danaevent.team.royaume")).isTrue();

        teamManager.disbandTeam("royaume").get();

        assertThat(teamManager.getTeam("royaume")).isEmpty();
        assertThat(teamManager.isColorAvailable(TeamColor.LIME)).isTrue();
        assertThat(leader.hasPermission("danaevent.team.royaume")).isFalse();
    }

    @Test
    @DisplayName("handlePlayerJoin and handlePlayerQuit should manage dynamic permissions")
    void shouldHandlePlayerConnections() throws ExecutionException, InterruptedException {
        PlayerMock player = server.addPlayer("ConnTester");
        teamManager.createTeam("faucons", "Les Faucons", TeamColor.WHITE, player).get();

        assertThat(player.hasPermission("danaevent.team.faucons")).isTrue();

        // Disconnect
        teamManager.handlePlayerQuit(player);

        // Reconnect
        teamManager.handlePlayerJoin(player);
        assertThat(player.hasPermission("danaevent.team.faucons")).isTrue();
    }
}
