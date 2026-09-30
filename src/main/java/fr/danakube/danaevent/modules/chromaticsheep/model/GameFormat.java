package fr.danakube.danaevent.modules.chromaticsheep.model;

/**
 * Defines the game format for ChromaticSheep arenas.
 */
public enum GameFormat {
    SOLO,
    TEAM;

    public static GameFormat fromString(String str) {
        if (str == null) return SOLO;
        try {
            return valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SOLO;
        }
    }
}
