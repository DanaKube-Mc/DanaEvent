package fr.danakube.danaevent.modules.treasurehunt.model;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a configured treasure hunt adventure.
 */
public class Hunt {

    private final String id;
    private String displayName;
    private HuntMode mode;
    private HuntPathType pathType;
    private final List<HuntStep> steps = new ArrayList<>();
    private ItemStack finalRewardItem;
    private final List<String> finalRewardCommands = new ArrayList<>();
    private boolean enabled;

    public Hunt(@NotNull String id, @NotNull String displayName, @NotNull HuntMode mode, @NotNull HuntPathType pathType) {
        this.id = Objects.requireNonNull(id, "id cannot be null").trim().toLowerCase();
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
        this.mode = Objects.requireNonNull(mode, "mode cannot be null");
        this.pathType = Objects.requireNonNull(pathType, "pathType cannot be null");
        this.enabled = true;
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(@NotNull String displayName) {
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
    }

    public @NotNull HuntMode getMode() {
        return mode;
    }

    public void setMode(@NotNull HuntMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode cannot be null");
    }

    public @NotNull HuntPathType getPathType() {
        return pathType;
    }

    public void setPathType(@NotNull HuntPathType pathType) {
        this.pathType = Objects.requireNonNull(pathType, "pathType cannot be null");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public @NotNull List<HuntStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    public int getStepCount() {
        return steps.size();
    }

    public void addStep(@NotNull HuntStep step) {
        Objects.requireNonNull(step, "step cannot be null");
        steps.add(step);
    }

    public boolean removeStep(int stepNumber) {
        return steps.removeIf(s -> s.getStepNumber() == stepNumber);
    }

    public Optional<HuntStep> getStep(int stepNumber) {
        return steps.stream()
            .filter(s -> s.getStepNumber() == stepNumber)
            .findFirst();
    }

    public @Nullable ItemStack getFinalRewardItem() {
        return finalRewardItem != null ? finalRewardItem.clone() : null;
    }

    public void setFinalRewardItem(@Nullable ItemStack finalRewardItem) {
        this.finalRewardItem = finalRewardItem != null ? finalRewardItem.clone() : null;
    }

    public @NotNull List<String> getFinalRewardCommands() {
        return new ArrayList<>(finalRewardCommands);
    }

    public void setFinalRewardCommands(@Nullable List<String> commands) {
        this.finalRewardCommands.clear();
        if (commands != null) {
            this.finalRewardCommands.addAll(commands);
        }
    }

    public void addFinalRewardCommand(@NotNull String command) {
        this.finalRewardCommands.add(Objects.requireNonNull(command, "command cannot be null"));
    }

    public boolean isReady() {
        return !steps.isEmpty();
    }
}
