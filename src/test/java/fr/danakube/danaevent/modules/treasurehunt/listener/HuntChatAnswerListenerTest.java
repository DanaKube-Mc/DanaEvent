package fr.danakube.danaevent.modules.treasurehunt.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HuntChatAnswerListenerTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private DanaEventPlugin plugin;
    private HuntConfig huntConfig;
    private TreasureHuntDatabase database;
    private HuntProgressManager progressManager;
    private HuntChatAnswerListener listener;
    private Hunt testHunt;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);

        File configFile = new File(tempDir.toFile(), "hunts.yml");
        huntConfig = new HuntConfig(configFile);

        database = new TreasureHuntDatabase(plugin.getDatabaseManager(), Runnable::run);
        database.initTables();

        progressManager = new HuntProgressManager(plugin, huntConfig, database);

        testHunt = huntConfig.createHunt("riddles", "Énigmes du Sphinx", HuntMode.SOLO, HuntPathType.LINEAR_STATIC);
        
        HuntStep step1 = new HuntStep(1, StepTriggerType.CHAT_ANSWER);
        step1.setChatAnswer("sésame");
        testHunt.addStep(step1);

        HuntStep step2 = new HuntStep(2, StepTriggerType.CHAT_ANSWER);
        step2.setChatAnswer("abracadabra");
        testHunt.addStep(step2);

        huntConfig.saveHunts();

        listener = new HuntChatAnswerListener(plugin, progressManager, huntConfig);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        if (listener != null) {
            listener.cleanUp();
        }
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private String getPlainMessage(Component component) {
        return component != null ? PlainTextComponentSerializer.plainText().serialize(component) : "";
    }

    @Test
    @DisplayName("Typing correct secret answer cancels chat event and advances step")
    void shouldCancelEventAndAdvanceStepOnCorrectAnswer() {
        PlayerMock player = server.addPlayer("Riddler");
        progressManager.startHunt(player.getUniqueId(), false, "riddles");

        org.bukkit.event.player.AsyncPlayerChatEvent event = new org.bukkit.event.player.AsyncPlayerChatEvent(
            false,
            player,
            "Sésame",
            java.util.Collections.emptySet()
        );
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();

        PlayerHuntProgress progress = progressManager.getActiveProgress(player.getUniqueId()).orElseThrow();
        assertThat(progress.getActiveStepNumber()).isEqualTo(2);

        Component msg = player.nextComponentMessage();
        assertThat(getPlainMessage(msg)).contains("Objectif accompli");
    }

    @Test
    @DisplayName("Typing normal unrelated message does not cancel chat and does not advance step")
    void shouldNotCancelUnrelatedChat() {
        PlayerMock player = server.addPlayer("Chatter");
        progressManager.startHunt(player.getUniqueId(), false, "riddles");

        org.bukkit.event.player.AsyncPlayerChatEvent event = new org.bukkit.event.player.AsyncPlayerChatEvent(
            false,
            player,
            "Bonjour tout le monde !",
            java.util.Collections.emptySet()
        );
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();

        PlayerHuntProgress progress = progressManager.getActiveProgress(player.getUniqueId()).orElseThrow();
        assertThat(progress.getActiveStepNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("Typing future step secret answer cancels chat event to hide secret and sends error")
    void shouldCancelAndRejectFutureStepAnswer() {
        PlayerMock player = server.addPlayer("Guesser");
        progressManager.startHunt(player.getUniqueId(), false, "riddles");

        // Player is at step 1, but says step 2's password "abracadabra"
        org.bukkit.event.player.AsyncPlayerChatEvent event = new org.bukkit.event.player.AsyncPlayerChatEvent(
            false,
            player,
            "abracadabra",
            java.util.Collections.emptySet()
        );
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();

        PlayerHuntProgress progress = progressManager.getActiveProgress(player.getUniqueId()).orElseThrow();
        assertThat(progress.getActiveStepNumber()).isEqualTo(1);

        Component msg = player.nextComponentMessage();
        assertThat(getPlainMessage(msg)).contains("force mystique");
    }
}
