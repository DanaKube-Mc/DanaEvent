package fr.danakube.danaevent.modules.deacoudre.model;

/**
 * Defines whether the Dé à Coudre match is played solo or in teams.
 */
public enum DacGameFormat {
    SOLO,
    TEAM;

    public static DacGameFormat fromString(String str) {
        if (str == null) return SOLO;
        String upper = str.trim().toUpperCase();
        try {
            return valueOf(upper);
        } catch (IllegalArgumentException e) {
            return SOLO;
        }
    }
}
