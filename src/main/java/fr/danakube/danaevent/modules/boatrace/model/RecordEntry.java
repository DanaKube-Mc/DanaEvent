package fr.danakube.danaevent.modules.boatrace.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record representing a player's track record time.
 *
 * @param id database record id (0 if not yet persisted)
 * @param trackId unique identifier of the track
 * @param playerUuid UUID of the player
 * @param timeMillis elapsed race time in milliseconds
 * @param laps number of laps completed
 * @param periodMonth period key (e.g., "2026-09" or "ALL_TIME")
 * @param createdAt timestamp of record creation
 */
public record RecordEntry(
    long id,
    String trackId,
    UUID playerUuid,
    long timeMillis,
    int laps,
    String periodMonth,
    Instant createdAt
) {

    public RecordEntry {
        Objects.requireNonNull(trackId, "trackId cannot be null");
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(periodMonth, "periodMonth cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
    }

    /**
     * Convenience constructor without id (defaults to 0).
     */
    public RecordEntry(String trackId, UUID playerUuid, long timeMillis, int laps, String periodMonth, Instant createdAt) {
        this(0L, trackId, playerUuid, timeMillis, laps, periodMonth, createdAt);
    }

    /**
     * Formats this record's time into "MM:SS.mmm" format.
     *
     * @return formatted string representation of time
     */
    public String formatTime() {
        return formatTime(this.timeMillis);
    }

    /**
     * Formats milliseconds into "MM:SS.mmm" format (e.g. 01:14.285).
     *
     * @param millis time in milliseconds
     * @return formatted time string
     */
    public static String formatTime(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        long minutes = millis / 60000;
        long seconds = (millis % 60000) / 1000;
        long ms = millis % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, ms);
    }
}
