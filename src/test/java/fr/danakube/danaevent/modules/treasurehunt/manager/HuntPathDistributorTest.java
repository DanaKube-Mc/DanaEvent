package fr.danakube.danaevent.modules.treasurehunt.manager;

import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntMode;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntPathType;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntStep;
import fr.danakube.danaevent.modules.treasurehunt.model.StepTriggerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class HuntPathDistributorTest {

    @Test
    @DisplayName("LINEAR_STATIC should always produce exact ascending step order")
    void shouldProduceLinearOrder() {
        Hunt hunt = new Hunt("linear_hunt", "Linear", HuntMode.SOLO, HuntPathType.LINEAR_STATIC);
        hunt.addStep(new HuntStep(1, StepTriggerType.BLOCK_CLICK));
        hunt.addStep(new HuntStep(2, StepTriggerType.BLOCK_CLICK));
        hunt.addStep(new HuntStep(3, StepTriggerType.BLOCK_CLICK));
        hunt.addStep(new HuntStep(4, StepTriggerType.BLOCK_CLICK));

        HuntPathDistributor distributor = new HuntPathDistributor();
        List<Integer> order1 = distributor.generateStepOrder(hunt);
        List<Integer> order2 = distributor.generateStepOrder(hunt);

        assertThat(order1).containsExactly(1, 2, 3, 4);
        assertThat(order2).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("RANDOM_PERMUTATION should permute intermediate steps while keeping final step fixed")
    void shouldPermuteIntermediateStepsAndKeepFinalFixed() {
        Hunt hunt = new Hunt("random_hunt", "Random", HuntMode.SOLO, HuntPathType.RANDOM_PERMUTATION);
        hunt.addStep(new HuntStep(1, StepTriggerType.BLOCK_CLICK));
        hunt.addStep(new HuntStep(2, StepTriggerType.BLOCK_CLICK));
        hunt.addStep(new HuntStep(3, StepTriggerType.BLOCK_CLICK));
        hunt.addStep(new HuntStep(4, StepTriggerType.BLOCK_CLICK));
        hunt.addStep(new HuntStep(5, StepTriggerType.BLOCK_CLICK)); // final step 5

        // Seeded random to guarantee permutation
        Random fixedRandom = new Random(42);
        HuntPathDistributor distributor = new HuntPathDistributor(fixedRandom);
        List<Integer> order = distributor.generateStepOrder(hunt);

        assertThat(order).hasSize(5);
        // The last element MUST always be step 5 (the final treasure)
        assertThat(order.get(4)).isEqualTo(5);
        // All intermediate steps (1, 2, 3, 4) must be present
        assertThat(order.subList(0, 4)).containsExactlyInAnyOrder(1, 2, 3, 4);
    }

    @Test
    @DisplayName("RANDOM_PERMUTATION with 2 or fewer steps should preserve linear order")
    void shouldPreserveSmallHunts() {
        Hunt hunt = new Hunt("small_hunt", "Small", HuntMode.SOLO, HuntPathType.RANDOM_PERMUTATION);
        hunt.addStep(new HuntStep(1, StepTriggerType.BLOCK_CLICK));
        hunt.addStep(new HuntStep(2, StepTriggerType.BLOCK_CLICK));

        HuntPathDistributor distributor = new HuntPathDistributor();
        List<Integer> order = distributor.generateStepOrder(hunt);

        assertThat(order).containsExactly(1, 2);
    }
}
