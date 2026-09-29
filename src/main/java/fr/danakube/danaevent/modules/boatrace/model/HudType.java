package fr.danakube.danaevent.modules.boatrace.model;

/**
 * Supported display modes for the race chrono HUD.
 */
public enum HudType {
    BOSS_BAR("BossBar"),
    ACTION_BAR("Action Bar"),
    BOTH("BossBar & Action Bar");

    private final String displayName;

    HudType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static HudType fromString(String str) {
        if (str == null || str.isBlank()) {
            return BOSS_BAR;
        }
        return switch (str.trim().toLowerCase()) {
            case "actionbar", "action_bar", "action" -> ACTION_BAR;
            case "both", "all", "tout" -> BOTH;
            case "bossbar", "boss_bar", "boss" -> BOSS_BAR;
            default -> null;
        };
    }
}
