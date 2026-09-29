package fr.danakube.danaevent.modules.boatrace.model;

/**
 * Operating mode of a boat race track.
 */
public enum TrackMode {
    /**
     * Continuous 24/7 time attack mode with solo timing and leaderboards.
     */
    TIME_ATTACK_247,

    /**
     * Scheduled or manual competitive event mode with multiple concurrent racers.
     */
    EVENT_COMPETITION
}
