package fr.danakube.danaevent.core.team.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DanaTeamTest {

    @Test
    @DisplayName("Should create team with leader and handle member additions and removals")
    void shouldCreateTeamAndManageMembers() {
        UUID leaderUuid = UUID.randomUUID();
        UUID memberUuid = UUID.randomUUID();

        DanaTeam team = new DanaTeam("tigres", "Les Tigres", TeamColor.ORANGE, leaderUuid);
        TeamMember leader = new TeamMember(leaderUuid, "LeaderPlayer", TeamRole.LEADER, Instant.now());
        team.addMember(leader);

        assertThat(team.getId()).isEqualTo("tigres");
        assertThat(team.getDisplayName()).isEqualTo("Les Tigres");
        assertThat(team.getColor()).isEqualTo(TeamColor.ORANGE);
        assertThat(team.isLeader(leaderUuid)).isTrue();
        assertThat(team.hasMember(leaderUuid)).isTrue();
        assertThat(team.getMemberCount()).isEqualTo(1);
        assertThat(team.getFormattedName()).isEqualTo("<orange>Les Tigres</orange>");

        // Add member
        TeamMember member = new TeamMember(memberUuid, "MemberPlayer", TeamRole.MEMBER, Instant.now());
        team.addMember(member);
        assertThat(team.hasMember(memberUuid)).isTrue();
        assertThat(team.getMemberCount()).isEqualTo(2);

        // Remove member
        team.removeMember(memberUuid);
        assertThat(team.hasMember(memberUuid)).isFalse();
        assertThat(team.getMemberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("setLeader should demote previous leader and promote new leader")
    void shouldTransferLeadership() {
        UUID oldLeaderUuid = UUID.randomUUID();
        UUID newLeaderUuid = UUID.randomUUID();

        DanaTeam team = new DanaTeam("eclairs", "Les Éclairs", TeamColor.YELLOW, oldLeaderUuid);
        team.addMember(new TeamMember(oldLeaderUuid, "OldLeader", TeamRole.LEADER));
        team.addMember(new TeamMember(newLeaderUuid, "NewLeader", TeamRole.MEMBER));

        team.setLeader(newLeaderUuid);

        assertThat(team.getLeaderUuid()).isEqualTo(newLeaderUuid);
        assertThat(team.isLeader(newLeaderUuid)).isTrue();
        assertThat(team.isLeader(oldLeaderUuid)).isFalse();

        assertThat(team.getMember(oldLeaderUuid).orElseThrow().role()).isEqualTo(TeamRole.MEMBER);
        assertThat(team.getMember(newLeaderUuid).orElseThrow().role()).isEqualTo(TeamRole.LEADER);
    }

    @Test
    @DisplayName("setLeader should fail if target player is not in the team")
    void shouldRejectLeaderNotMember() {
        UUID oldLeaderUuid = UUID.randomUUID();
        UUID outsiderUuid = UUID.randomUUID();

        DanaTeam team = new DanaTeam("dragons", "Les Dragons", TeamColor.RED, oldLeaderUuid);
        team.addMember(new TeamMember(oldLeaderUuid, "LeaderPlayer", TeamRole.LEADER));

        assertThatThrownBy(() -> team.setLeader(outsiderUuid))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("New leader must already be a member");
    }

    @Test
    @DisplayName("TeamInvite should properly calculate expiry and remaining seconds")
    void shouldHandleTeamInviteExpiry() throws InterruptedException {
        UUID inviter = UUID.randomUUID();
        UUID invited = UUID.randomUUID();

        TeamInvite invite = TeamInvite.create("dragons", inviter, invited, 2L);
        assertThat(invite.isExpired()).isFalse();
        assertThat(invite.getRemainingSeconds()).isGreaterThan(0L);

        // Past invite
        TeamInvite expiredInvite = new TeamInvite(
            "dragons",
            inviter,
            invited,
            Instant.now().minusSeconds(100),
            Instant.now().minusSeconds(10)
        );
        assertThat(expiredInvite.isExpired()).isTrue();
        assertThat(expiredInvite.getRemainingSeconds()).isZero();
    }
}
