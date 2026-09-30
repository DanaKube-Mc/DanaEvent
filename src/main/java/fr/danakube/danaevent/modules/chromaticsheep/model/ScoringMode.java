package fr.danakube.danaevent.modules.chromaticsheep.model;

/**
 * Defines the scoring calculation mode in a ChromaticSheep arena.
 */
public enum ScoringMode {
    FINAL_COUNT,
    DOMINATION_TICK,
    ACTION_SCORE;

    public static ScoringMode fromString(String str) {
        if (str == null) return FINAL_COUNT;
        String upper = str.trim().toUpperCase();
        if (upper.equals("ACTION") || upper.equals("ACTION_SCORE")) {
            return ACTION_SCORE;
        }
        if (upper.equals("DOMINATION") || upper.equals("DOMINATION_TICK")) {
            return DOMINATION_TICK;
        }
        if (upper.equals("FINAL") || upper.equals("FINAL_COUNT")) {
            return FINAL_COUNT;
        }
        try {
            return valueOf(upper);
        } catch (IllegalArgumentException e) {
            return FINAL_COUNT;
        }
    }
}
