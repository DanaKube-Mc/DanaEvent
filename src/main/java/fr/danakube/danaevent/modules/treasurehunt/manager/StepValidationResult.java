package fr.danakube.danaevent.modules.treasurehunt.manager;

/**
 * Outcome resulting from an attempt to validate a hunt step.
 */
public enum StepValidationResult {
    STEP_ADVANCED,
    HUNT_COMPLETED,
    WRONG_STEP,
    NOT_IN_HUNT,
    HUNT_DISABLED
}
