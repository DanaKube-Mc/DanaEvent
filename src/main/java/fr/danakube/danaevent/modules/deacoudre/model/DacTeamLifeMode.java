package fr.danakube.danaevent.modules.deacoudre.model;

/**
 * Defines life management in Team Dé à Coudre matches.
 */
public enum DacTeamLifeMode {
    LAST_STANDING,
    SHARED_POOL;

    public static DacTeamLifeMode fromString(String str) {
        if (str == null) return LAST_STANDING;
        String upper = str.trim().toUpperCase();
        if (upper.equals("SHARED") || upper.equals("POOL") || upper.equals("SHARED_POOL")) {
            return SHARED_POOL;
        }
        if (upper.equals("LAST") || upper.equals("LAST_STANDING") || upper.equals("INDIVIDUAL")) {
            return LAST_STANDING;
        }
        try {
            return valueOf(upper);
        } catch (IllegalArgumentException e) {
            return LAST_STANDING;
        }
    }
}
