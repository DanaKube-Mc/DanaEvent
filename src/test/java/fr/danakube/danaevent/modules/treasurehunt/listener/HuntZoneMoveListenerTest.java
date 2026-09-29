package fr.danakube.danaevent.modules.treasurehunt.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.selection.CuboidRegion;
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
import org.bukkit.Location;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HuntZoneMoveListenerTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private DanaEventPlugin plugin;
    private WorldMock world;
    private HuntConfig huntConfig;
    private TreasureHuntDatabase database;
    private HuntProgressManager progressManager;
    private HuntZoneMoveListener listener;
    private Hunt testHunt;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        world = server.addSimpleWorld("world");

        File configFile = new File(tempDir.toFile(), "hunts.yml");
        huntConfig = new HuntConfig(configFile);

        database = new TreasureHuntDatabase(plugin.getDatabaseManager(), Runnable::run);
        database.initTables();

        progressManager = new HuntProgressManager(plugin, huntConfig, database);

        testHunt = huntConfig.createHunt("caves", "Grottes Mystérieuses", HuntMode.SOLO, HuntPathType.LINEAR_STATIC);
        
        HuntStep step1 = new HuntStep(1, StepTriggerType.ZONE_ENTER);
        step1.setTargetRegion(new CuboidRegion("world", 10, 60, 10, 15, 65, 15));
        testHunt.addStep(step1);

        HuntStep step2 = new HuntStep(2, StepTriggerType.ZONE_ENTER);
        step2.setTargetRegion(new CuboidRegion("world", 50, 60, 50, 55, 65, 55));
        testHunt.addStep(step2);

        huntConfig.saveHunts();

        listener = new HuntZoneMoveListener(plugin, progressManager, huntConfig);
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
    @DisplayName("Player entering target region of their active step advances step and receives notification")
    void shouldValidateStepOnEnteringRegion() {
        PlayerMock player = server.addPlayer("Explorer");
        progressManager.startHunt(player.getUniqueId(), false, "caves");

        Location from = new Location(world, 5, 64, 5);
        Location to = new Location(world, 12, 62, 12); // inside target region of step 1

        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        server.getPluginManager().callEvent(event);

        PlayerHuntProgress progress = progressManager.getActiveProgress(player.getUniqueId()).orElseThrow();
        assertThat(progress.getActiveStepNumber()).isEqualTo(2);

        Component msg = player.nextComponentMessage();
        assertThat(getPlainMessage(msg)).contains("Objectif accompli");
    }

    @Test
    @DisplayName("Moving outside target region does not advance step")
    void shouldNotValidateWhenOutsideRegion() {
        PlayerMock player = server.addPlayer("Wanderer");
        progressManager.startHunt(player.getUniqueId(), false, "caves");

        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 1, 64, 1);

        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        server.getPluginManager().callEvent(event);

        PlayerHuntProgress progress = progressManager.getActiveProgress(player.getUniqueId()).orElseThrow();
        assertThat(progress.getActiveStepNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("Entering step 2 region while still on step 1 does not trigger step 2")
    void shouldNotValidateFutureRegionPrematurely() {
        PlayerMock player = server.addPlayer("Skipper");
        progressManager.startHunt(player.getUniqueId(), false, "caves");

        // Moves into step 2 region at (52, 62, 52)
        Location from = new Location(world, 48, 64, 48);
        Location to = new Location(world, 52, 62, 52);

        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        server.getPluginManager().callEvent(event);

        PlayerHuntProgress progress = progressManager.getActiveProgress(player.getUniqueId()).orElseThrow();
        assertThat(progress.getActiveStepNumber()).isEqualTo(1); // remains 1
    }
}
