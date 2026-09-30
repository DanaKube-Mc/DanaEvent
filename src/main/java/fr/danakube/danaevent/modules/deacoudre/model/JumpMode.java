package fr.danakube.danaevent.modules.deacoudre.model;

/**
 * Defines the jumping mode in a Dé à Coudre arena.
 */
public enum JumpMode {
    TURN_BY_TURN,
    SIMULTANEOUS_WAVE;

    public static JumpMode fromString(String str) {
        if (str == null) return TURN_BY_TURN;
        String upper = str.trim().toUpperCase();
        if (upper.equals("TURN") || upper.equals("TURN_BY_TURN") || upper.equals("SOLO_TURN")) {
            return TURN_BY_TURN;
        }
        if (upper.equals("WAVE") || upper.equals("SIMULTANEOUS") || upper.equals("SIMULTANEOUS_WAVE")) {
            return SIMULTANEOUS_WAVE;
        }
        try {
            return valueOf(upper);
        } catch (IllegalArgumentException e) {
            return TURN_BY_TURN;
        }
    }
}
