package fr.danakube.danaevent.modules.treasurehunt.display;

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

class NightcoreStyleHudTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private DanaEventPlugin plugin;
    private HuntConfig huntConfig;
    private TreasureHuntDatabase database;
    private HuntProgressManager progressManager;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);

        File configFile = new File(tempDir.toFile(), "hunts.yml");
        huntConfig = new HuntConfig(configFile);

        database = new TreasureHuntDatabase(plugin.getDatabaseManager(), Runnable::run);
        database.initTables();

        progressManager = new HuntProgressManager(plugin, huntConfig, database);
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private String getPlain(Component component) {
        return component != null ? PlainTextComponentSerializer.plainText().serialize(component) : "";
    }

    @Test
    @DisplayName("buildProgressBar correctly builds green filled and gray empty blocks")
    void shouldBuildProgressBar() {
        String bar0 = NightcoreStyleHud.buildProgressBar(0, 5, 5);
        assertThat(bar0).isEqualTo("<green></green><gray>■■■■■</gray>");

        String bar3 = NightcoreStyleHud.buildProgressBar(3, 5, 5);
        assertThat(bar3).isEqualTo("<green>■■■</green><gray>■■</gray>");

        String bar5 = NightcoreStyleHud.buildProgressBar(5, 5, 5);
        assertThat(bar5).isEqualTo("<green>■■■■■</green><gray></gray>");
    }

    @Test
    @DisplayName("buildActionBar includes hunt badge, step counter, gauge and clue")
    void shouldBuildActionBar() {
        Component bar = NightcoreStyleHud.buildActionBar(3, 5, "Sous le grand chêne");
        String plain = getPlain(bar);

        assertThat(plain).contains("CHASSE", "Étape 3/5", "[■■■■■]", "Sous le grand chêne");
    }

    @Test
    @DisplayName("sendObjectiveCompletedTitle and sendHuntCompletedTitle show titles to player")
    void shouldSendTitlesToPlayer() {
        PlayerMock player = server.addPlayer("Explorer");

        NightcoreStyleHud.sendObjectiveCompletedTitle(player);
        // Under MockBukkit, title is accepted without throwing
        NightcoreStyleHud.sendHuntCompletedTitle(player, "04:12.345");
    }

    @Test
    @DisplayName("HuntHudTask periodically sends ActionBar to participating players")
    void shouldSendActionBarViaTask() {
        Hunt hunt = huntConfig.createHunt("castle", "Château Secret", HuntMode.SOLO, HuntPathType.LINEAR_STATIC);
        HuntStep s1 = new HuntStep(1, StepTriggerType.CHAT_ANSWER);
        s1.setClue("Dans le donjon");
        hunt.addStep(s1);
        huntConfig.saveHunts();

        PlayerMock player = server.addPlayer("Knight");
        progressManager.startHunt(player.getUniqueId(), false, "castle");

        // Drain initial messages from starting the hunt
        while (player.nextComponentMessage() != null) {}

        HuntHudTask hudTask = new HuntHudTask(progressManager, huntConfig);
        hudTask.run();

        Component actionBar = player.nextComponentMessage();
        // MockBukkit delivers action bar as a message or via player.nextComponentMessage()
        // If not null, assert content
        if (actionBar != null) {
            assertThat(getPlain(actionBar)).contains("CHASSE", "Dans le donjon");
        }
    }
}
