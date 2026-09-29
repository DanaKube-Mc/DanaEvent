package fr.danakube.danaevent.modules.treasurehunt.model;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HuntTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("hunt_world");
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should create Hunt and manage lifecycle attributes and steps")
    void shouldCreateHuntAndManageSteps() {
        Hunt hunt = new Hunt("pirate_cove", "Crique des Pirates", HuntMode.SOLO, HuntPathType.RANDOM_PERMUTATION);
        assertThat(hunt.getId()).isEqualTo("pirate_cove");
        assertThat(hunt.getDisplayName()).isEqualTo("Crique des Pirates");
        assertThat(hunt.getMode()).isEqualTo(HuntMode.SOLO);
        assertThat(hunt.getPathType()).isEqualTo(HuntPathType.RANDOM_PERMUTATION);
        assertThat(hunt.isEnabled()).isTrue();
        assertThat(hunt.isReady()).isFalse();

        HuntStep step1 = new HuntStep(1, StepTriggerType.BLOCK_CLICK);
        step1.setClue("<gray>Trouvez le coffre</gray>");
        hunt.addStep(step1);

        assertThat(hunt.getStepCount()).isEqualTo(1);
        assertThat(hunt.isReady()).isTrue();
        assertThat(hunt.getStep(1)).contains(step1);

        hunt.setFinalRewardItem(new ItemStack(Material.DIAMOND, 5));
        hunt.addFinalRewardCommand("eco give %player% 1000");

        assertThat(hunt.getFinalRewardItem()).isNotNull();
        assertThat(hunt.getFinalRewardItem().getType()).isEqualTo(Material.DIAMOND);
        assertThat(hunt.getFinalRewardCommands()).containsExactly("eco give %player% 1000");

        assertThat(hunt.removeStep(1)).isTrue();
        assertThat(hunt.getStepCount()).isEqualTo(0);
        assertThat(hunt.isReady()).isFalse();
    }

    @Test
    @DisplayName("Should correctly match chat answers case-insensitively")
    void shouldMatchChatAnswer() {
        HuntStep step = new HuntStep(1, StepTriggerType.CHAT_ANSWER);
        step.setChatAnswer("abracadabra");

        assertThat(step.matchesChatAnswer("abracadabra")).isTrue();
        assertThat(step.matchesChatAnswer("  ABRACADABRA  ")).isTrue();
        assertThat(step.matchesChatAnswer("wrong")).isFalse();
        assertThat(step.matchesChatAnswer(null)).isFalse();

        // Different trigger type
        step.setTriggerType(StepTriggerType.BLOCK_CLICK);
        assertThat(step.matchesChatAnswer("abracadabra")).isFalse();
    }

    @Test
    @DisplayName("Should correctly match block click locations")
    void shouldMatchBlockLocation() {
        Location target = new Location(world, 10, 64, -20);
        HuntStep step = new HuntStep(1, StepTriggerType.BLOCK_CLICK);
        step.setTargetLocation(target);

        Location sameBlock = new Location(world, 10.4, 64.9, -19.2);
        Location differentBlock = new Location(world, 11, 64, -20);

        assertThat(step.matchesBlockLocation(sameBlock)).isTrue();
        assertThat(step.matchesBlockLocation(differentBlock)).isFalse();
        assertThat(step.matchesBlockLocation(null)).isFalse();
    }

    @Test
    @DisplayName("Should correctly match region for ZONE_ENTER triggers")
    void shouldMatchRegion() {
        CuboidRegion region = new CuboidRegion(
            new Location(world, 0, 60, 0),
            new Location(world, 10, 70, 10)
        );
        HuntStep step = new HuntStep(1, StepTriggerType.ZONE_ENTER);
        step.setTargetRegion(region);

        Location inside = new Location(world, 5, 65, 5);
        Location outside = new Location(world, 15, 65, 5);

        assertThat(step.matchesRegion(inside)).isTrue();
        assertThat(step.matchesRegion(outside)).isFalse();
    }

    @Test
    @DisplayName("Enums should parse safely with fallbacks")
    void shouldParseEnumsSafely() {
        assertThat(HuntMode.fromString("team")).isEqualTo(HuntMode.TEAM);
        assertThat(HuntMode.fromString("unknown")).isEqualTo(HuntMode.SOLO);
        assertThat(HuntMode.fromString(null)).isEqualTo(HuntMode.SOLO);

        assertThat(HuntPathType.fromString("random_permutation")).isEqualTo(HuntPathType.RANDOM_PERMUTATION);
        assertThat(HuntPathType.fromString(null)).isEqualTo(HuntPathType.LINEAR_STATIC);

        assertThat(StepTriggerType.fromString("zone_enter")).isEqualTo(StepTriggerType.ZONE_ENTER);
        assertThat(StepTriggerType.fromString("chat_answer")).isEqualTo(StepTriggerType.CHAT_ANSWER);
        assertThat(StepTriggerType.fromString(null)).isEqualTo(StepTriggerType.BLOCK_CLICK);
    }
}
