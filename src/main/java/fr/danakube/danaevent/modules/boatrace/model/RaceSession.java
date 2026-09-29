package fr.danakube.danaevent.modules.boatrace.model;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Boat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents an active race session for a player on a track.
 */
public class RaceSession {

    private final UUID playerUuid;
    private final Track track;
    private Boat boat;
    private UUID boatUuid;
    private long startTimeMillis;
    private Material boatMaterial;
    private int currentLap;
    private final List<Long> lapTimes = new ArrayList<>();
    private long lastLapCrossingMillis;
    private RaceState state;
    private long finalElapsedTimeMillis = -1;
    private final BossBar bossBar;
    private HudType hudType;

    public RaceSession(UUID playerUuid, Track track, Boat boat, long startTimeMillis, RaceState state) {
        this(playerUuid, track, boat, startTimeMillis, state, null, HudType.BOSS_BAR);
    }

    public RaceSession(UUID playerUuid, Track track, Boat boat, long startTimeMillis, RaceState state, Material boatMaterial) {
        this(playerUuid, track, boat, startTimeMillis, state, boatMaterial, HudType.BOSS_BAR);
    }

    public RaceSession(UUID playerUuid, Track track, Boat boat, long startTimeMillis, RaceState state, Material boatMaterial, HudType hudType) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        this.track = Objects.requireNonNull(track, "track cannot be null");
        this.boat = boat;
        this.boatUuid = boat != null ? boat.getUniqueId() : null;
        this.startTimeMillis = startTimeMillis;
        this.boatMaterial = boatMaterial;
        this.lastLapCrossingMillis = 0L;
        this.state = state != null ? state : RaceState.COUNTDOWN;
        this.currentLap = 1;
        this.hudType = hudType != null ? hudType : HudType.BOSS_BAR;
        this.bossBar = BossBar.bossBar(
            Component.empty(),
            1.0f,
            BossBar.Color.BLUE,
            BossBar.Overlay.PROGRESS
        );
    }

    /**
     * Calculates the elapsed time in milliseconds.
     * If the session is finished or cancelled with recorded final time, returns that value.
     * Otherwise, returns current time minus startTimeMillis.
     *
     * @return elapsed time in milliseconds
     */
    public long getElapsedTimeMillis() {
        if (finalElapsedTimeMillis >= 0) {
            return finalElapsedTimeMillis;
        }
        return Math.max(0, System.currentTimeMillis() - startTimeMillis);
    }

    /**
     * Checks if enough time has elapsed since the last line crossing to debounce false triggers.
     *
     * @param minIntervalMillis minimum required interval in ms
     * @param currentMillis current timestamp in ms
     * @return true if crossing is permitted, false if debounced
     */
    public boolean canCrossLine(long minIntervalMillis, long currentMillis) {
        if (lastLapCrossingMillis <= 0) {
            return true;
        }
        return (currentMillis - lastLapCrossingMillis) >= minIntervalMillis;
    }

    /**
     * Records a completed lap duration in milliseconds.
     *
     * @param lapTimeMillis lap duration in milliseconds
     */
    public void recordLap(long lapTimeMillis) {
        this.lapTimes.add(lapTimeMillis);
    }

    /**
     * Formats milliseconds into "MM:SS.mmm" format (e.g. 01:14.285).
     *
     * @param millis time in milliseconds
     * @return formatted time string
     */
    public static String formatTime(long millis) {
        return RecordEntry.formatTime(millis);
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public Track getTrack() {
        return track;
    }

    public Material getBoatMaterial() {
        return boatMaterial;
    }

    public void setBoatMaterial(Material boatMaterial) {
        this.boatMaterial = boatMaterial;
    }

    public void setBoat(Boat boat) {
        this.boat = boat;
        this.boatUuid = boat != null ? boat.getUniqueId() : null;
    }

    public void setStartTimeMillis(long startTimeMillis) {
        this.startTimeMillis = startTimeMillis;
    }

    public void resetLaps() {
        this.lapTimes.clear();
        this.currentLap = 0;
        this.lastLapCrossingMillis = 0L;
    }

    public Boat getBoat() {
        return boat;
    }

    public UUID getBoatUuid() {
        return boatUuid;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public int getCurrentLap() {
        return currentLap;
    }

    public void setCurrentLap(int currentLap) {
        this.currentLap = currentLap;
    }

    public List<Long> getLapTimes() {
        return Collections.unmodifiableList(lapTimes);
    }

    public long getLastLapCrossingMillis() {
        return lastLapCrossingMillis;
    }

    public void setLastLapCrossingMillis(long lastLapCrossingMillis) {
        this.lastLapCrossingMillis = lastLapCrossingMillis;
    }

    public RaceState getState() {
        return state;
    }

    public void setState(RaceState state) {
        this.state = Objects.requireNonNull(state, "state cannot be null");
        if ((state == RaceState.FINISHED || state == RaceState.CANCELLED) && finalElapsedTimeMillis < 0) {
            this.finalElapsedTimeMillis = Math.max(0, System.currentTimeMillis() - startTimeMillis);
        }
    }

    public long getFinalElapsedTimeMillis() {
        return finalElapsedTimeMillis;
    }

    public void setFinalElapsedTimeMillis(long finalElapsedTimeMillis) {
        this.finalElapsedTimeMillis = finalElapsedTimeMillis;
    }

    public BossBar getBossBar() {
        return bossBar;
    }

    public HudType getHudType() {
        return hudType;
    }

    public void setHudType(HudType hudType) {
        this.hudType = hudType != null ? hudType : HudType.BOSS_BAR;
    }
}
