package fr.danakube.danaevent.modules.treasurehunt.model;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks the live progress of a player or team during an active treasure hunt.
 */
public class PlayerHuntProgress {

    private final UUID holderUuid;
    private final boolean isTeam;
    private final String huntId;
    private int currentStepIndex;
    private final List<Integer> stepOrder;
    private long startTimeMillis;
    private long completedTimeMillis;
    private boolean completed;

    public PlayerHuntProgress(
        @NotNull UUID holderUuid,
        boolean isTeam,
        @NotNull String huntId,
        int currentStepIndex,
        @NotNull List<Integer> stepOrder,
        long startTimeMillis,
        long completedTimeMillis,
        boolean completed
    ) {
        this.holderUuid = Objects.requireNonNull(holderUuid, "holderUuid cannot be null");
        this.isTeam = isTeam;
        this.huntId = Objects.requireNonNull(huntId, "huntId cannot be null").trim().toLowerCase();
        this.currentStepIndex = Math.max(0, currentStepIndex);
        this.stepOrder = new ArrayList<>(Objects.requireNonNull(stepOrder, "stepOrder cannot be null"));
        this.startTimeMillis = startTimeMillis;
        this.completedTimeMillis = completedTimeMillis;
        this.completed = completed;
    }

    public static PlayerHuntProgress start(
        @NotNull UUID holderUuid,
        boolean isTeam,
        @NotNull String huntId,
        @NotNull List<Integer> stepOrder
    ) {
        return new PlayerHuntProgress(
            holderUuid,
            isTeam,
            huntId,
            0,
            stepOrder,
            System.currentTimeMillis(),
            -1L,
            false
        );
    }

    public @NotNull UUID getHolderUuid() {
        return holderUuid;
    }

    public boolean isTeam() {
        return isTeam;
    }

    public @NotNull String getHuntId() {
        return huntId;
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    public void setCurrentStepIndex(int currentStepIndex) {
        this.currentStepIndex = currentStepIndex;
    }

    public @NotNull List<Integer> getStepOrder() {
        return Collections.unmodifiableList(stepOrder);
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public void setStartTimeMillis(long startTimeMillis) {
        this.startTimeMillis = startTimeMillis;
    }

    public long getCompletedTimeMillis() {
        return completedTimeMillis;
    }

    public boolean isCompleted() {
        return completed;
    }

    /**
     * Retrieves the 1-based step number currently expected by the player.
     *
     * @return active step number, or -1 if no steps or already completed
     */
    public int getActiveStepNumber() {
        if (completed || currentStepIndex < 0 || currentStepIndex >= stepOrder.size()) {
            return -1;
        }
        return stepOrder.get(currentStepIndex);
    }

    /**
     * Checks if the player is currently on their final step.
     *
     * @return true if on last step
     */
    public boolean isLastStep() {
        return !stepOrder.isEmpty() && currentStepIndex >= stepOrder.size() - 1;
    }

    /**
     * Advances to the next step.
     */
    public void advanceStep() {
        this.currentStepIndex++;
    }

    /**
     * Marks the hunt completed.
     *
     * @param timestampMillis completion epoch millis
     */
    public void markCompleted(long timestampMillis) {
        this.completed = true;
        this.completedTimeMillis = timestampMillis;
    }

    /**
     * Computes the total elapsed race time in milliseconds.
     *
     * @return elapsed time in ms
     */
    public long getElapsedTimeMillis() {
        if (completed && completedTimeMillis >= 0) {
            return Math.max(0, completedTimeMillis - startTimeMillis);
        }
        return Math.max(0, System.currentTimeMillis() - startTimeMillis);
    }

    /**
     * Formats the elapsed time into MM:SS.mmm format.
     *
     * @return formatted time string
     */
    public String formatElapsedTime() {
        return formatTime(getElapsedTimeMillis());
    }

    public static String formatTime(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;
        long ms = millis % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, ms);
    }
}
