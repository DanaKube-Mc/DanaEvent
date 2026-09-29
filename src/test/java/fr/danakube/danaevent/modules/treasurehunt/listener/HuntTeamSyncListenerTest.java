package fr.danakube.danaevent.modules.treasurehunt.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.team.manager.TeamManager;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import fr.danakube.danaevent.modules.treasurehunt.config.HuntConfig;
import fr.danakube.danaevent.modules.treasurehunt.database.TreasureHuntDatabase;
import fr.danakube.danaevent.modules.treasurehunt.manager.HuntProgressManager;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntMode;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntPathType;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntStep;
import fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress;
import fr.danakube.danaevent.modules.treasurehunt.model.StepTriggerType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class HuntTeamSyncListenerTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private DanaEventPlugin plugin;
    private TeamManager teamManager;
    private HuntConfig huntConfig;
    private TreasureHuntDatabase database;
    private HuntProgressManager progressManager;
    private HuntTeamSyncListener listener;
    private Hunt testHunt;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        teamManager = plugin.getTeamManager();

        File configFile = new File(tempDir.toFile(), "hunts.yml");
        huntConfig = new HuntConfig(configFile);

        database = new TreasureHuntDatabase(plugin.getDatabaseManager(), Runnable::run);
        database.initTables();

        progressManager = new HuntProgressManager(plugin, huntConfig, database);

        testHunt = huntConfig.createHunt("pirate_cove", "Crique des Pirates", HuntMode.TEAM, HuntPathType.LINEAR_STATIC);
        
        HuntStep s1 = new HuntStep(1, StepTriggerType.CHAT_ANSWER);
        s1.setChatAnswer("jack");
        testHunt.addStep(s1);

        HuntStep s2 = new HuntStep(2, StepTriggerType.CHAT_ANSWER);
        s2.setChatAnswer("perle");
        testHunt.addStep(s2);

        huntConfig.saveHunts();

        listener = new HuntTeamSyncListener(plugin, progressManager, huntConfig);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private String getPlainMessage(Component component) {
        return component != null ? PlainTextComponentSerializer.plainText().serialize(component) : "";
    }

    @Test
    @DisplayName("Validating step by one team member advances progress and broadcasts to all teammates")
    void shouldBroadcastStepAdvancementToAllTeammates() throws ExecutionException, InterruptedException {
        PlayerMock leader = server.addPlayer("Captain");
        PlayerMock mate = server.addPlayer("Sailor");

        DanaTeam team = teamManager.createTeam("corsairs", "Les Corsaires", TeamColor.RED, leader).get();
        teamManager.invitePlayer(team.getId(), leader.getUniqueId(), mate);
        leader.nextComponentMessage(); // consume invite sent msg
        mate.nextComponentMessage(); // consume invite received msg

        teamManager.acceptInvite(mate, team.getId()).get();
        leader.nextComponentMessage(); // consume invite accepted broadcast
        mate.nextComponentMessage(); // consume invite accepted broadcast for mate

        UUID teamUuid = HuntProgressManager.getTeamUuid(team.getId());
        progressManager.startHunt(teamUuid, true, "pirate_cove");

        // Captain validates step 1
        progressManager.validateStepForPlayer(leader, 1).get();

        // Sailor must receive team broadcast
        Component sailorMsg = mate.nextComponentMessage();
        assertThat(sailorMsg).isNotNull();
        assertThat(getPlainMessage(sailorMsg)).contains("[Équipe]", "Captain", "étape 1");

        // Both players now share step 2
        PlayerHuntProgress leaderProgress = progressManager.getProgressForPlayer(leader).orElseThrow();
        PlayerHuntProgress sailorProgress = progressManager.getProgressForPlayer(mate).orElseThrow();
        assertThat(leaderProgress.getActiveStepNumber()).isEqualTo(2);
        assertThat(sailorProgress.getActiveStepNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("Reconnecting team member receives team hunt reconnect notification")
    void shouldNotifyReconnectingTeamMember() throws ExecutionException, InterruptedException {
        PlayerMock leader = server.addPlayer("LeaderOne");
        DanaTeam team = teamManager.createTeam("titans", "Les Titans", TeamColor.BLUE, leader).get();

        UUID teamUuid = HuntProgressManager.getTeamUuid(team.getId());
        progressManager.startHunt(teamUuid, true, "pirate_cove");

        // Another member joins later
        PlayerMock joiningMember = server.addPlayer("LateHero");
        team.addMember(new fr.danakube.danaevent.core.team.model.TeamMember(
            joiningMember.getUniqueId(),
            joiningMember.getName(),
            fr.danakube.danaevent.core.team.model.TeamRole.MEMBER,
            java.time.Instant.now()
        ));
        plugin.getTeamDatabase().addMember(team.getId(), team.getMember(joiningMember.getUniqueId()).orElseThrow()).get();
        teamManager.loadAllTeams().get();

        // Trigger PlayerJoinEvent for late member
        PlayerJoinEvent joinEvent = new PlayerJoinEvent(joiningMember, Component.text("joined"));
        server.getPluginManager().callEvent(joinEvent);

        Component msg = joiningMember.nextComponentMessage();
        assertThat(msg).isNotNull();
        assertThat(getPlainMessage(msg)).contains("Crique des Pirates", "Étape 1/2");
    }

    @Test
    @DisplayName("Solo player reconnecting resumes hunt from database and receives notification")
    void shouldResumeSoloHuntOnReconnect() throws ExecutionException, InterruptedException {
        PlayerMock player = server.addPlayer("SoloRanger");

        // Save orphaned progress in database
        PlayerHuntProgress orphan = PlayerHuntProgress.start(
            player.getUniqueId(),
            false,
            "pirate_cove",
            List.of(1, 2)
        );
        orphan.advanceStep(); // at step 2
        database.saveProgress(orphan).get();

        // Disconnect and reconnect
        PlayerJoinEvent joinEvent = new PlayerJoinEvent(player, Component.text("joined"));
        server.getPluginManager().callEvent(joinEvent);

        assertThat(progressManager.isParticipant(player.getUniqueId())).isTrue();
        Component msg = player.nextComponentMessage();
        assertThat(msg).isNotNull();
        assertThat(getPlainMessage(msg)).contains("Crique des Pirates", "Étape 2/2");
    }

    @Test
    @DisplayName("Disbanding team cancels active team hunt and notifies online members")
    void shouldCancelHuntOnTeamDisband() throws ExecutionException, InterruptedException {
        PlayerMock leader = server.addPlayer("Boss");
        DanaTeam team = teamManager.createTeam("vikings", "Les Vikings", TeamColor.BLUE, leader).get();
        leader.nextComponentMessage(); // consume team created

        UUID teamUuid = HuntProgressManager.getTeamUuid(team.getId());
        progressManager.startHunt(teamUuid, true, "pirate_cove");
        assertThat(progressManager.isParticipant(teamUuid)).isTrue();

        // Disband team
        teamManager.disbandTeam(team.getId()).get();

        // Hunt must be cancelled
        assertThat(progressManager.isParticipant(teamUuid)).isFalse();

        // Leader received team disbanded and hunt cancelled messages
        Component msg1 = leader.nextComponentMessage();
        Component msg2 = leader.nextComponentMessage();
        assertThat(getPlainMessage(msg1) + " " + getPlainMessage(msg2)).contains("chasse au trésor", "annulée");
    }
}
