package fr.danakube.danaevent.modules.chromaticsheep.model;

/**
 * Types of special sheep that can appear during a ChromaticSheep game.
 */
public enum SpecialSheepType {
    NORMAL,
    GOLDEN,
    RAINBOW,
    TRICKSTER;

    public static SpecialSheepType fromString(String str) {
        if (str == null) return NORMAL;
        try {
            return valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
