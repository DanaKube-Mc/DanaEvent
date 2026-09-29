package fr.danakube.danaevent.modules.treasurehunt.model;

/**
 * Defines whether a hunt is played individually or in teams.
 */
public enum HuntMode {
    SOLO,
    TEAM;

    public static HuntMode fromString(String str) {
        if (str == null) return SOLO;
        try {
            return valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SOLO;
        }
    }
}
