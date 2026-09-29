package fr.danakube.danaevent.modules.treasurehunt.model;

/**
 * Defines the trajectory mode for treasure hunts to prevent sheep behavior.
 */
public enum HuntPathType {
    LINEAR_STATIC,
    RANDOM_PERMUTATION;

    public static HuntPathType fromString(String str) {
        if (str == null) return LINEAR_STATIC;
        try {
            return valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LINEAR_STATIC;
        }
    }
}
