package fr.danakube.danaevent.modules.treasurehunt.model;

import fr.danakube.danaevent.core.selection.CuboidRegion;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single objective/step in a treasure hunt.
 */
public class HuntStep {

    private final int stepNumber;
    private StepTriggerType triggerType;
    private Location targetLocation;
    private CuboidRegion targetRegion;
    private String chatAnswer;
    private String npcId;
    private String clue;
    private ItemStack rewardItem;
    private final List<String> rewardCommands = new ArrayList<>();

    public HuntStep(int stepNumber, @NotNull StepTriggerType triggerType) {
        this.stepNumber = stepNumber;
        this.triggerType = Objects.requireNonNull(triggerType, "triggerType cannot be null");
        this.clue = "<gray>Aucun indice défini pour cette étape.</gray>";
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public @NotNull StepTriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(@NotNull StepTriggerType triggerType) {
        this.triggerType = Objects.requireNonNull(triggerType, "triggerType cannot be null");
    }

    public @Nullable Location getTargetLocation() {
        return targetLocation;
    }

    public void setTargetLocation(@Nullable Location targetLocation) {
        this.targetLocation = targetLocation;
    }

    public @Nullable CuboidRegion getTargetRegion() {
        return targetRegion;
    }

    public void setTargetRegion(@Nullable CuboidRegion targetRegion) {
        this.targetRegion = targetRegion;
    }

    public @Nullable String getChatAnswer() {
        return chatAnswer;
    }

    public void setChatAnswer(@Nullable String chatAnswer) {
        this.chatAnswer = chatAnswer;
    }

    public @Nullable String getNpcId() {
        return npcId;
    }

    public void setNpcId(@Nullable String npcId) {
        this.npcId = npcId;
    }

    public @NotNull String getClue() {
        return clue;
    }

    public void setClue(@NotNull String clue) {
        this.clue = Objects.requireNonNull(clue, "clue cannot be null");
    }

    public @Nullable ItemStack getRewardItem() {
        return rewardItem != null ? rewardItem.clone() : null;
    }

    public void setRewardItem(@Nullable ItemStack rewardItem) {
        this.rewardItem = rewardItem != null ? rewardItem.clone() : null;
    }

    public @NotNull List<String> getRewardCommands() {
        return new ArrayList<>(rewardCommands);
    }

    public void setRewardCommands(@Nullable List<String> rewardCommands) {
        this.rewardCommands.clear();
        if (rewardCommands != null) {
            this.rewardCommands.addAll(rewardCommands);
        }
    }

    public void addRewardCommand(@NotNull String command) {
        this.rewardCommands.add(Objects.requireNonNull(command, "command cannot be null"));
    }

    /**
     * Checks if a player's typed message matches the step's secret answer.
     *
     * @param message input string from chat
     * @return true if matches, false otherwise
     */
    public boolean matchesChatAnswer(@Nullable String message) {
        if (triggerType != StepTriggerType.CHAT_ANSWER || chatAnswer == null || message == null) {
            return false;
        }
        return chatAnswer.trim().equalsIgnoreCase(message.trim());
    }

    /**
     * Checks if a target location matches the block location of this step.
     *
     * @param location the location to test
     * @return true if same block coordinates and world
     */
    public boolean matchesBlockLocation(@Nullable Location location) {
        if (triggerType != StepTriggerType.BLOCK_CLICK || targetLocation == null || location == null) {
            return false;
        }
        if (targetLocation.getWorld() == null || location.getWorld() == null) {
            return false;
        }
        if (!targetLocation.getWorld().getName().equals(location.getWorld().getName())) {
            return false;
        }
        return targetLocation.getBlockX() == location.getBlockX()
            && targetLocation.getBlockY() == location.getBlockY()
            && targetLocation.getBlockZ() == location.getBlockZ();
    }

    /**
     * Checks if a location is inside this step's target region.
     *
     * @param location location to check
     * @return true if location is contained
     */
    public boolean matchesRegion(@Nullable Location location) {
        if (triggerType != StepTriggerType.ZONE_ENTER || targetRegion == null || location == null) {
            return false;
        }
        return targetRegion.contains(location);
    }
}
