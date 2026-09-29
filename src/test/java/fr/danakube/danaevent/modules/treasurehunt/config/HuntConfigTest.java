package fr.danakube.danaevent.modules.treasurehunt.config;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntMode;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntPathType;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntStep;
import fr.danakube.danaevent.modules.treasurehunt.model.StepTriggerType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HuntConfigTest {

    private ServerMock server;
    private WorldMock world;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("hunt_config_world");
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("loadHunts should handle missing file gracefully")
    void shouldHandleMissingFile() {
        File file = new File(tempDir.toFile(), "missing_hunts.yml");
        HuntConfig config = new HuntConfig(file);
        config.loadHunts();
        assertThat(config.getHunts()).isEmpty();
    }

    @Test
    @DisplayName("Should serialize hunts with steps to YAML and reload with full fidelity")
    void shouldSerializeAndReloadHunts() {
        File file = new File(tempDir.toFile(), "hunts.yml");
        HuntConfig config = new HuntConfig(file);

        Hunt hunt = config.createHunt("isle_of_skulls", "L'Île aux Crânes", HuntMode.TEAM, HuntPathType.RANDOM_PERMUTATION);
        hunt.setFinalRewardItem(new ItemStack(Material.NETHERITE_INGOT, 2));
        hunt.addFinalRewardCommand("broadcast %player% a terminé l'aventure !");

        // Step 1: BLOCK_CLICK
        HuntStep step1 = new HuntStep(1, StepTriggerType.BLOCK_CLICK);
        step1.setClue("<yellow>Trouvez le crâne doré sur la colline.</yellow>");
        step1.setTargetLocation(new Location(world, 100, 64, 200));
        step1.setRewardItem(new ItemStack(Material.GOLDEN_APPLE, 3));
        hunt.addStep(step1);

        // Step 2: ZONE_ENTER
        HuntStep step2 = new HuntStep(2, StepTriggerType.ZONE_ENTER);
        step2.setClue("<aqua>Franchissez le passage sous la cascade.</aqua>");
        step2.setTargetRegion(new CuboidRegion(
            new Location(world, 50, 50, 50),
            new Location(world, 60, 60, 60)
        ));
        step2.addRewardCommand("eco give %player% 250");
        hunt.addStep(step2);

        // Step 3: CHAT_ANSWER
        HuntStep step3 = new HuntStep(3, StepTriggerType.CHAT_ANSWER);
        step3.setClue("<gray>Énoncez le mot de passe de l'ancien capitaine.</gray>");
        step3.setChatAnswer("blackbeard");
        hunt.addStep(step3);

        // Save
        config.saveHunts();
        assertThat(file.exists()).isTrue();

        // Reload into a fresh HuntConfig instance
        HuntConfig reloaded = new HuntConfig(file);
        reloaded.loadHunts();

        assertThat(reloaded.getHunts()).hasSize(1);
        Optional<Hunt> loadedHuntOpt = reloaded.getHunt("isle_of_skulls");
        assertThat(loadedHuntOpt).isPresent();

        Hunt loaded = loadedHuntOpt.get();
        assertThat(loaded.getId()).isEqualTo("isle_of_skulls");
        assertThat(loaded.getDisplayName()).isEqualTo("L'Île aux Crânes");
        assertThat(loaded.getMode()).isEqualTo(HuntMode.TEAM);
        assertThat(loaded.getPathType()).isEqualTo(HuntPathType.RANDOM_PERMUTATION);
        assertThat(loaded.isEnabled()).isTrue();
        assertThat(loaded.getFinalRewardItem()).isNotNull();
        assertThat(loaded.getFinalRewardItem().getType()).isEqualTo(Material.NETHERITE_INGOT);
        assertThat(loaded.getFinalRewardCommands()).containsExactly("broadcast %player% a terminé l'aventure !");

        assertThat(loaded.getStepCount()).isEqualTo(3);

        // Verify Step 1
        HuntStep loadedS1 = loaded.getStep(1).orElseThrow();
        assertThat(loadedS1.getTriggerType()).isEqualTo(StepTriggerType.BLOCK_CLICK);
        assertThat(loadedS1.getClue()).contains("crâne doré");
        assertThat(loadedS1.getTargetLocation()).isNotNull();
        assertThat(loadedS1.getTargetLocation().getBlockX()).isEqualTo(100);
        assertThat(loadedS1.getRewardItem()).isNotNull();
        assertThat(loadedS1.getRewardItem().getType()).isEqualTo(Material.GOLDEN_APPLE);

        // Verify Step 2
        HuntStep loadedS2 = loaded.getStep(2).orElseThrow();
        assertThat(loadedS2.getTriggerType()).isEqualTo(StepTriggerType.ZONE_ENTER);
        assertThat(loadedS2.getTargetRegion()).isNotNull();
        assertThat(loadedS2.getTargetRegion().contains(new Location(world, 55, 55, 55))).isTrue();
        assertThat(loadedS2.getRewardCommands()).containsExactly("eco give %player% 250");

        // Verify Step 3
        HuntStep loadedS3 = loaded.getStep(3).orElseThrow();
        assertThat(loadedS3.getTriggerType()).isEqualTo(StepTriggerType.CHAT_ANSWER);
        assertThat(loadedS3.getChatAnswer()).isEqualTo("blackbeard");
        assertThat(loadedS3.matchesChatAnswer("BLACKBEARD")).isTrue();

        // Delete
        assertThat(reloaded.deleteHunt("isle_of_skulls")).isTrue();
        assertThat(reloaded.getHunt("isle_of_skulls")).isEmpty();
    }
}
