package fr.danakube.danaevent.modules.treasurehunt.display;

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
import fr.danakube.danaevent.modules.treasurehunt.model.StepTriggerType;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

class ClueParticleTaskTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private DanaEventPlugin plugin;
    private WorldMock world;
    private HuntConfig huntConfig;
    private TreasureHuntDatabase database;
    private HuntProgressManager progressManager;

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

        Hunt hunt = huntConfig.createHunt("pyramid", "Pyramide", HuntMode.SOLO, HuntPathType.LINEAR_STATIC);
        HuntStep s1 = new HuntStep(1, StepTriggerType.BLOCK_CLICK);
        s1.setTargetLocation(new Location(world, 10, 64, 10));
        hunt.addStep(s1);

        HuntStep s2 = new HuntStep(2, StepTriggerType.ZONE_ENTER);
        s2.setTargetRegion(new CuboidRegion("world", 20, 60, 20, 30, 70, 30));
        hunt.addStep(s2);
        huntConfig.saveHunts();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("ClueParticleTask runs cleanly for players near target locations and regions")
    void shouldRunClueParticleTask() {
        PlayerMock player = server.addPlayer("Explorer");
        player.teleport(new Location(world, 10, 64, 12)); // within 32 blocks of step 1

        progressManager.startHunt(player.getUniqueId(), false, "pyramid");

        ClueParticleTask task = new ClueParticleTask(progressManager, huntConfig);
        task.run(); // step 1 block click particle

        // Advance to step 2 zone enter
        progressManager.validateStep(player.getUniqueId(), 1);
        player.teleport(new Location(world, 25, 65, 25)); // inside step 2 zone
        task.run(); // step 2 region particle
    }
}
