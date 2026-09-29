package fr.danakube.danaevent.modules.treasurehunt.model;

/**
 * Trigger conditions required to complete a step in a treasure hunt.
 */
public enum StepTriggerType {
    BLOCK_CLICK,
    ZONE_ENTER,
    CHAT_ANSWER,
    NPC_INTERACT;

    public static StepTriggerType fromString(String str) {
        if (str == null) return BLOCK_CLICK;
        try {
            return valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BLOCK_CLICK;
        }
    }
}
