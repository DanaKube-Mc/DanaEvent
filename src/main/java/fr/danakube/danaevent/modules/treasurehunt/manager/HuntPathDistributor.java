package fr.danakube.danaevent.modules.treasurehunt.manager;

import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntPathType;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntStep;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Generates trajectory step orders for treasure hunt participants,
 * applying the anti-sheep permutation algorithm when configured.
 */
public class HuntPathDistributor {

    private final Random random;

    public HuntPathDistributor() {
        this(new Random());
    }

    public HuntPathDistributor(@NotNull Random random) {
        this.random = Objects.requireNonNull(random, "random cannot be null");
    }

    /**
     * Generates the ordered list of step numbers for a player or team.
     * In LINEAR_STATIC mode, preserves standard order.
     * In RANDOM_PERMUTATION mode, shuffles all intermediate steps while keeping
     * the final treasure step fixed at the end.
     *
     * @param hunt the hunt configuration
     * @return ordered list of step numbers
     */
    public List<Integer> generateStepOrder(@NotNull Hunt hunt) {
        Objects.requireNonNull(hunt, "hunt cannot be null");
        List<HuntStep> steps = hunt.getSteps();
        if (steps.isEmpty()) {
            return List.of();
        }

        List<Integer> stepNumbers = steps.stream()
            .map(HuntStep::getStepNumber)
            .toList();

        if (hunt.getPathType() == HuntPathType.LINEAR_STATIC || stepNumbers.size() <= 2) {
            return new ArrayList<>(stepNumbers);
        }

        // Shuffle intermediate steps [0 .. N-2], keep final step N-1 fixed
        int finalStep = stepNumbers.get(stepNumbers.size() - 1);
        List<Integer> intermediate = new ArrayList<>(stepNumbers.subList(0, stepNumbers.size() - 1));
        Collections.shuffle(intermediate, random);

        List<Integer> result = new ArrayList<>(intermediate);
        result.add(finalStep);
        return result;
    }
}
