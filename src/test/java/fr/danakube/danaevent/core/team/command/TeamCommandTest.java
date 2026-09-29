package fr.danakube.danaevent.core.team.command;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.team.manager.TeamManager;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeamCommandTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private TeamManager teamManager;

    private PlayerMock captain;
    private PlayerMock recruit;
    private PlayerMock admin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        teamManager = plugin.getTeamManager();

        captain = server.addPlayer("Captain");
        recruit = server.addPlayer("Recruit");
        admin = server.addPlayer("Admin");
        admin.addAttachment(plugin, TeamAdminCmd.PERMISSION, true);
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Nested
    @DisplayName("Player Team Commands (/de team)")
    class PlayerCommandsTests {

        @Test
        @DisplayName("Should reject /de team create without permission")
        void shouldRejectCreateWithoutPermission() {
            captain.performCommand("de team create Spartans");

            String msg = captain.nextMessage();
            assertThat(msg).contains("Vous n'avez pas la permission");
        }

        @Test
        @DisplayName("Should create team with permission and assign leader")
        void shouldCreateTeamWithPermission() {
            captain.addAttachment(plugin, "danaevent.team.create", true);

            captain.performCommand("de team create Spartans");

            // Wait async
            waitFor(() -> teamManager.hasTeam(captain.getUniqueId()));

            assertThat(teamManager.hasTeam(captain.getUniqueId())).isTrue();
            DanaTeam team = teamManager.getPlayerTeam(captain.getUniqueId()).orElseThrow();
            assertThat(team.getId()).isEqualTo("spartans");
            assertThat(team.getDisplayName()).isEqualTo("Spartans");
            assertThat(team.isLeader(captain.getUniqueId())).isTrue();
            assertThat(captain.hasPermission("danaevent.team.spartans")).isTrue();

            String msg = captain.nextMessage();
            assertThat(msg).contains("a été créée avec succès");
        }

        @Test
        @DisplayName("Should reject team creation with invalid name characters")
        void shouldRejectInvalidName() {
            captain.addAttachment(plugin, "danaevent.team.create", true);

            captain.performCommand("de team create !!");

            String msg = captain.nextMessage();
            assertThat(msg).contains("identifiant d'équipe est invalide");
        }

        @Test
        @DisplayName("Should invite player, accept invite, and sync membership")
        void shouldInviteAndAccept() {
            captain.addAttachment(plugin, "danaevent.team.create", true);
            captain.performCommand("de team create Vikings");
            waitFor(() -> teamManager.hasTeam(captain.getUniqueId()));

            // Drain previous messages
            drainMessages(captain);
            drainMessages(recruit);

            // Captain invites Recruit
            captain.performCommand("de team invite Recruit");

            waitForMessage(recruit, "vous a invité à rejoindre l'équipe");
            waitForMessage(captain, "Invitation envoyée à Recruit");

            // Recruit accepts
            recruit.performCommand("de team accept vikings");
            waitFor(() -> teamManager.hasTeam(recruit.getUniqueId()));

            assertThat(teamManager.hasTeam(recruit.getUniqueId())).isTrue();
            DanaTeam team = teamManager.getTeam("vikings").orElseThrow();
            assertThat(team.getMemberCount()).isEqualTo(2);
            assertThat(recruit.hasPermission("danaevent.team.vikings")).isTrue();
        }

        @Test
        @DisplayName("Should allow leader to kick member")
        void shouldAllowLeaderToKickMember() {
            captain.addAttachment(plugin, "danaevent.team.create", true);
            captain.performCommand("de team create Knights");
            waitFor(() -> teamManager.hasTeam(captain.getUniqueId()));

            captain.performCommand("de team invite Recruit");
            recruit.performCommand("de team accept knights");
            waitFor(() -> teamManager.hasTeam(recruit.getUniqueId()));

            // Drain messages before kick
            drainMessages(captain);
            drainMessages(recruit);

            // Captain kicks Recruit
            captain.performCommand("de team kick Recruit");
            waitFor(() -> !teamManager.hasTeam(recruit.getUniqueId()));

            assertThat(teamManager.hasTeam(recruit.getUniqueId())).isFalse();
            waitForMessage(recruit, "Vous avez été expulsé de l'équipe");
        }

        @Test
        @DisplayName("Should disband team when leader leaves")
        void shouldDisbandWhenLeaderLeaves() {
            captain.addAttachment(plugin, "danaevent.team.create", true);
            captain.performCommand("de team create Pirates");
            waitFor(() -> teamManager.hasTeam(captain.getUniqueId()));

            TeamColor color = teamManager.getTeam("pirates").orElseThrow().getColor();
            assertThat(teamManager.isColorAvailable(color)).isFalse();

            captain.performCommand("de team leave");
            waitFor(() -> !teamManager.hasTeam(captain.getUniqueId()));

            assertThat(teamManager.getTeam("pirates")).isEmpty();
            assertThat(teamManager.isColorAvailable(color)).isTrue();
        }

        @Test
        @DisplayName("Should display team info with members and color")
        void shouldDisplayTeamInfo() {
            captain.addAttachment(plugin, "danaevent.team.create", true);
            captain.performCommand("de team create Ninjas");
            waitFor(() -> teamManager.hasTeam(captain.getUniqueId()));

            captain.performCommand("de team info");

            // Drain messages
            boolean foundName = false;
            String msg;
            while ((msg = captain.nextMessage()) != null) {
                if (msg.contains("Ninjas")) foundName = true;
            }
            assertThat(foundName).isTrue();
        }
    }

    @Nested
    @DisplayName("Admin Team Commands (/de teamadmin)")
    class AdminCommandsTests {

        @Test
        @DisplayName("Should reject admin commands without permission")
        void shouldRejectWithoutPermission() {
            captain.performCommand("de teamadmin disband any");

            String msg = captain.nextMessage();
            assertThat(msg).contains("Vous n'avez pas la permission");
        }

        @Test
        @DisplayName("Should force disband team as admin")
        void shouldForceDisbandAsAdmin() {
            captain.addAttachment(plugin, "danaevent.team.create", true);
            captain.performCommand("de team create Gladiators");
            waitFor(() -> teamManager.hasTeam(captain.getUniqueId()));

            admin.performCommand("de teamadmin disband gladiators");
            waitFor(() -> !teamManager.hasTeam(captain.getUniqueId()));

            assertThat(teamManager.getTeam("gladiators")).isEmpty();
            waitForMessage(admin, "a été dissoute de force");
        }

        @Test
        @DisplayName("Should force set team color as admin")
        void shouldForceSetColorAsAdmin() {
            captain.addAttachment(plugin, "danaevent.team.create", true);
            captain.performCommand("de team create Titans");
            waitFor(() -> teamManager.hasTeam(captain.getUniqueId()));

            admin.performCommand("de teamadmin setcolor titans purple");
            waitFor(() -> teamManager.getTeam("titans").orElseThrow().getColor() == TeamColor.PURPLE);

            assertThat(teamManager.getTeam("titans").orElseThrow().getColor()).isEqualTo(TeamColor.PURPLE);
            waitForMessage(admin, "a été changée en");
        }

        @Test
        @DisplayName("Should add points to team as admin")
        void shouldAddPointsAsAdmin() {
            captain.addAttachment(plugin, "danaevent.team.create", true);
            captain.performCommand("de team create Dragons");
            waitFor(() -> teamManager.hasTeam(captain.getUniqueId()));

            admin.performCommand("de teamadmin addpoints dragons boatrace 150");
            waitFor(() -> plugin.getTeamScoreManager().getTeamTotalScore("dragons", "boatrace", plugin.getTeamScoreManager().getCurrentPeriodMonth()).join() == 150.0);

            Double score = plugin.getTeamScoreManager().getTeamTotalScore("dragons", "boatrace", plugin.getTeamScoreManager().getCurrentPeriodMonth()).join();
            assertThat(score).isEqualTo(150.0);
            waitForMessage(admin, "points ont été attribués");
        }
    }

    @Nested
    @DisplayName("Tab Completion Tests")
    class TabCompletionTests {

        @Test
        @DisplayName("Should tab complete player subcommands")
        void shouldTabCompletePlayerSubcommands() {
            List<String> completions = server.getCommandTabComplete(captain, "de team ");
            assertThat(completions).contains("create", "color", "invite", "accept", "decline", "kick", "leave", "disband", "info", "top");
        }

        @Test
        @DisplayName("Should tab complete admin subcommands")
        void shouldTabCompleteAdminSubcommands() {
            List<String> completions = server.getCommandTabComplete(admin, "de teamadmin ");
            assertThat(completions).contains("disband", "setcolor", "addpoints");
        }
    }

    private void waitFor(java.util.function.BooleanSupplier condition) {
        long timeout = System.currentTimeMillis() + 2000L;
        while (System.currentTimeMillis() < timeout && !condition.getAsBoolean()) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {}
        }
    }

    private void waitForMessage(PlayerMock player, String snippet) {
        long timeout = System.currentTimeMillis() + 2000L;
        List<String> received = new java.util.ArrayList<>();
        while (System.currentTimeMillis() < timeout) {
            String msg = player.nextMessage();
            if (msg != null) {
                String plain = org.bukkit.ChatColor.stripColor(msg);
                received.add(plain);
                if (plain.contains(snippet)) {
                    return;
                }
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {}
        }
        throw new AssertionError("Message containing '" + snippet + "' not received by " + player.getName() + ". Received: " + received);
    }

    private List<String> drainMessages(PlayerMock player) {
        List<String> list = new java.util.ArrayList<>();
        String msg;
        while ((msg = player.nextMessage()) != null) {
            list.add(org.bukkit.ChatColor.stripColor(msg));
        }
        return list;
    }
}
