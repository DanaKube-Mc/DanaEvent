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
        try {
            return valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FINAL_COUNT;
        }
    }
}
