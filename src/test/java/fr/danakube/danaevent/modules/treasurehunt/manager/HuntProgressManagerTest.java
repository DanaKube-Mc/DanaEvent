package fr.danakube.danaevent.modules.treasurehunt.manager;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.core.database.DatabaseConfig;
import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.modules.treasurehunt.config.HuntConfig;
import fr.danakube.danaevent.modules.treasurehunt.database.TreasureHuntDatabase;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntMode;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntPathType;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntRecord;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntStep;
import fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress;
import fr.danakube.danaevent.modules.treasurehunt.model.StepTriggerType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class HuntProgressManagerTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private DatabaseManager databaseManager;
    private TreasureHuntDatabase database;
    private HuntConfig huntConfig;
    private HuntProgressManager progressManager;
    private Hunt testHunt;

    @BeforeEach
    void setUp() throws SQLException {
        server = MockBukkit.mock();

        DatabaseConfig config = new DatabaseConfig(
            StorageType.SQLITE,
            "localhost",
            3306,
            "treasurehunt_pm_test.db",
            "",
            "",
            5
        );
        databaseManager = new DatabaseManager(config, tempDir.toFile());
        database = new TreasureHuntDatabase(databaseManager, Runnable::run);
        database.initTables();

        File configFile = new File(tempDir.toFile(), "hunts.yml");
        huntConfig = new HuntConfig(configFile);

        // Linear static hunt: steps 1, 2, 3
        testHunt = huntConfig.createHunt("lost_temple", "Le Temple Perdu", HuntMode.SOLO, HuntPathType.LINEAR_STATIC);
        HuntStep s1 = new HuntStep(1, StepTriggerType.BLOCK_CLICK);
        s1.setRewardItem(new ItemStack(Material.GOLD_INGOT, 2));
        testHunt.addStep(s1);

        HuntStep s2 = new HuntStep(2, StepTriggerType.CHAT_ANSWER);
        testHunt.addStep(s2);

        HuntStep s3 = new HuntStep(3, StepTriggerType.ZONE_ENTER);
        testHunt.setFinalRewardItem(new ItemStack(Material.EMERALD, 5));
        testHunt.addStep(s3);

        huntConfig.saveHunts();

        progressManager = new HuntProgressManager(null, huntConfig, database);
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null && databaseManager.isRunning()) {
            databaseManager.shutdown();
        }
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should successfully start hunt, track active progress and reject duplicate starts")
    void shouldStartHuntAndTrackProgress() {
        PlayerMock player = server.addPlayer("Explorer");
        Optional<PlayerHuntProgress> progressOpt = progressManager.startHunt(player.getUniqueId(), false, "lost_temple");

        assertThat(progressOpt).isPresent();
        PlayerHuntProgress progress = progressOpt.get();
        assertThat(progress.getHuntId()).isEqualTo("lost_temple");
        assertThat(progress.getActiveStepNumber()).isEqualTo(1);
        assertThat(progressManager.isParticipant(player.getUniqueId())).isTrue();

        // Duplicate start must be rejected
        Optional<PlayerHuntProgress> secondStart = progressManager.startHunt(player.getUniqueId(), false, "lost_temple");
        assertThat(secondStart).isEmpty();
    }

    @Test
    @DisplayName("Should reject out-of-order step validation with WRONG_STEP without advancing")
    void shouldRejectOutOfOrderValidation() throws ExecutionException, InterruptedException {
        PlayerMock player = server.addPlayer("Cheater");
        progressManager.startHunt(player.getUniqueId(), false, "lost_temple");

        // Player is at step 1, but tries to validate step 2 or step 3
        StepValidationResult res2 = progressManager.validateStep(player.getUniqueId(), 2).get();
        assertThat(res2).isEqualTo(StepValidationResult.WRONG_STEP);

        StepValidationResult res3 = progressManager.validateStep(player.getUniqueId(), 3).get();
        assertThat(res3).isEqualTo(StepValidationResult.WRONG_STEP);

        // Active step should still be 1
        PlayerHuntProgress progress = progressManager.getActiveProgress(player.getUniqueId()).orElseThrow();
        assertThat(progress.getActiveStepNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should advance steps and complete hunt upon final step with record persistence")
    void shouldProgressAndCompleteHunt() throws ExecutionException, InterruptedException {
        PlayerMock player = server.addPlayer("Victor");
        progressManager.startHunt(player.getUniqueId(), false, "lost_temple");

        // Validate step 1 -> STEP_ADVANCED
        StepValidationResult step1Res = progressManager.validateStep(player.getUniqueId(), 1).get();
        assertThat(step1Res).isEqualTo(StepValidationResult.STEP_ADVANCED);
        PlayerHuntProgress p1 = progressManager.getActiveProgress(player.getUniqueId()).orElseThrow();
        assertThat(p1.getActiveStepNumber()).isEqualTo(2);

        // Intermediate reward check (2 Gold Ingots given to player)
        assertThat(player.getInventory().contains(Material.GOLD_INGOT, 2)).isTrue();

        // Validate step 2 -> STEP_ADVANCED
        StepValidationResult step2Res = progressManager.validateStep(player.getUniqueId(), 2).get();
        assertThat(step2Res).isEqualTo(StepValidationResult.STEP_ADVANCED);
        PlayerHuntProgress p2 = progressManager.getActiveProgress(player.getUniqueId()).orElseThrow();
        assertThat(p2.getActiveStepNumber()).isEqualTo(3);
        assertThat(p2.isLastStep()).isTrue();

        // Validate step 3 (final step) -> HUNT_COMPLETED
        StepValidationResult step3Res = progressManager.validateStep(player.getUniqueId(), 3).get();
        assertThat(step3Res).isEqualTo(StepValidationResult.HUNT_COMPLETED);

        // Final reward given (5 Emeralds)
        assertThat(player.getInventory().contains(Material.EMERALD, 5)).isTrue();

        // Session must be removed from memory
        assertThat(progressManager.isParticipant(player.getUniqueId())).isFalse();

        // Active progress must be deleted from DB
        assertThat(database.loadProgress(player.getUniqueId()).get()).isEmpty();

        // Record must be stored in database
        Optional<HuntRecord> pb = database.getPersonalBest("lost_temple", player.getUniqueId()).get();
        assertThat(pb).isPresent();
        assertThat(pb.get().timeMillis()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should resume orphaned hunt progress from database")
    void shouldResumeOrphanedProgress() throws ExecutionException, InterruptedException {
        PlayerMock player = server.addPlayer("ReconnectingPlayer");

        PlayerHuntProgress orphan = PlayerHuntProgress.start(
            player.getUniqueId(),
            false,
            "lost_temple",
            List.of(1, 2, 3)
        );
        orphan.advanceStep(); // at step 2
        database.saveProgress(orphan).get();

        // In-memory manager has nothing yet
        assertThat(progressManager.isParticipant(player.getUniqueId())).isFalse();

        // Resume from DB
        Optional<PlayerHuntProgress> resumed = progressManager.loadOrResumeProgress(player.getUniqueId()).get();
        assertThat(resumed).isPresent();
        assertThat(resumed.get().getActiveStepNumber()).isEqualTo(2);
        assertThat(progressManager.isParticipant(player.getUniqueId())).isTrue();
    }

    @Test
    @DisplayName("Should cancel hunt and clean up state from memory and database")
    void shouldCancelHunt() throws ExecutionException, InterruptedException {
        PlayerMock player = server.addPlayer("Quitter");
        progressManager.startHunt(player.getUniqueId(), false, "lost_temple");
        assertThat(progressManager.isParticipant(player.getUniqueId())).isTrue();

        boolean cancelled = progressManager.cancelHunt(player.getUniqueId()).get();
        assertThat(cancelled).isTrue();
        assertThat(progressManager.isParticipant(player.getUniqueId())).isFalse();
        assertThat(database.loadProgress(player.getUniqueId()).get()).isEmpty();
    }
}
