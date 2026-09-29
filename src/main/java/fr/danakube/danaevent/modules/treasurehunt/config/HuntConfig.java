package fr.danakube.danaevent.modules.treasurehunt.config;

import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntMode;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntPathType;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntStep;
import fr.danakube.danaevent.modules.treasurehunt.model.StepTriggerType;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages loading, saving, and querying Hunt configurations from hunts.yml.
 */
public class HuntConfig {

    static {
        ConfigurationSerialization.registerClass(CuboidRegion.class, "CuboidRegion");
    }

    private final File configFile;
    private final Map<String, Hunt> hunts = new LinkedHashMap<>();

    public HuntConfig(@NotNull JavaPlugin plugin) {
        this(new File(Objects.requireNonNull(plugin, "plugin cannot be null").getDataFolder(), "modules/treasurehunt/hunts.yml"));
    }

    public HuntConfig(@NotNull File configFile) {
        this.configFile = Objects.requireNonNull(configFile, "configFile cannot be null");
    }

    public @NotNull File getConfigFile() {
        return configFile;
    }

    public @NotNull Collection<Hunt> getHunts() {
        return Collections.unmodifiableCollection(hunts.values());
    }

    public Optional<Hunt> getHunt(@Nullable String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(hunts.get(id.trim().toLowerCase()));
    }

    public void registerHunt(@NotNull Hunt hunt) {
        Objects.requireNonNull(hunt, "hunt cannot be null");
        hunts.put(hunt.getId().trim().toLowerCase(), hunt);
    }

    public Hunt createHunt(@NotNull String id, @NotNull String displayName, @NotNull HuntMode mode, @NotNull HuntPathType pathType) {
        String cleanId = Objects.requireNonNull(id, "id cannot be null").trim().toLowerCase();
        if (hunts.containsKey(cleanId)) {
            throw new IllegalArgumentException("Hunt with ID '" + cleanId + "' already exists");
        }
        Hunt hunt = new Hunt(cleanId, displayName, mode, pathType);
        hunts.put(cleanId, hunt);
        return hunt;
    }

    public boolean deleteHunt(@Nullable String id) {
        if (id == null) {
            return false;
        }
        return hunts.remove(id.trim().toLowerCase()) != null;
    }

    public void clear() {
        hunts.clear();
    }

    /**
     * Saves all in-memory hunts to the YAML file.
     */
    public void saveHunts() {
        YamlConfiguration config = new YamlConfiguration();

        for (Hunt hunt : hunts.values()) {
            String path = "hunts." + hunt.getId();
            config.set(path + ".display_name", hunt.getDisplayName());
            config.set(path + ".mode", hunt.getMode().name());
            config.set(path + ".path_type", hunt.getPathType().name());
            config.set(path + ".enabled", hunt.isEnabled());

            if (hunt.getFinalRewardItem() != null) {
                config.set(path + ".final_reward_item", serializeItem(hunt.getFinalRewardItem()));
            }
            if (!hunt.getFinalRewardCommands().isEmpty()) {
                config.set(path + ".final_reward_commands", hunt.getFinalRewardCommands());
            }

            for (HuntStep step : hunt.getSteps()) {
                String stepPath = path + ".steps." + step.getStepNumber();
                config.set(stepPath + ".trigger", step.getTriggerType().name());
                config.set(stepPath + ".clue", step.getClue());

                if (step.getTargetLocation() != null) {
                    config.set(stepPath + ".location", step.getTargetLocation());
                }
                if (step.getTargetRegion() != null) {
                    config.set(stepPath + ".region", step.getTargetRegion());
                }
                if (step.getChatAnswer() != null) {
                    config.set(stepPath + ".chat_answer", step.getChatAnswer());
                }
                if (step.getNpcId() != null) {
                    config.set(stepPath + ".npc_id", step.getNpcId());
                }
                if (step.getRewardItem() != null) {
                    config.set(stepPath + ".reward_item", serializeItem(step.getRewardItem()));
                }
                if (!step.getRewardCommands().isEmpty()) {
                    config.set(stepPath + ".reward_commands", step.getRewardCommands());
                }
            }
        }

        if (configFile.getParentFile() != null && !configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save hunts configuration to " + configFile.getAbsolutePath(), e);
        }
    }

    /**
     * Loads hunts from the YAML configuration file.
     */
    public void loadHunts() {
        hunts.clear();

        if (!configFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection rootSection = config.getConfigurationSection("hunts");
        if (rootSection == null) {
            return;
        }

        for (String id : rootSection.getKeys(false)) {
            ConfigurationSection section = rootSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }

            String displayName = section.getString("display_name", id);
            HuntMode mode = HuntMode.fromString(section.getString("mode", "SOLO"));
            HuntPathType pathType = HuntPathType.fromString(section.getString("path_type", "LINEAR_STATIC"));
            boolean enabled = section.getBoolean("enabled", true);

            Hunt hunt = new Hunt(id, displayName, mode, pathType);
            hunt.setEnabled(enabled);

            if (section.contains("final_reward_item")) {
                hunt.setFinalRewardItem(deserializeItem(section.getString("final_reward_item")));
            }
            if (section.isList("final_reward_commands")) {
                hunt.setFinalRewardCommands(section.getStringList("final_reward_commands"));
            }

            ConfigurationSection stepsSection = section.getConfigurationSection("steps");
            if (stepsSection != null) {
                for (String stepKey : stepsSection.getKeys(false)) {
                    ConfigurationSection s = stepsSection.getConfigurationSection(stepKey);
                    if (s == null) {
                        continue;
                    }

                    int stepNumber;
                    try {
                        stepNumber = Integer.parseInt(stepKey);
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    StepTriggerType trigger = StepTriggerType.fromString(s.getString("trigger", "BLOCK_CLICK"));
                    HuntStep step = new HuntStep(stepNumber, trigger);
                    step.setClue(s.getString("clue", "<gray>Indice manquant.</gray>"));

                    if (s.isLocation("location")) {
                        step.setTargetLocation(s.getLocation("location"));
                    }
                    Object regObj = s.get("region");
                    if (regObj instanceof CuboidRegion reg) {
                        step.setTargetRegion(reg);
                    }
                    if (s.contains("chat_answer")) {
                        step.setChatAnswer(s.getString("chat_answer"));
                    }
                    if (s.contains("npc_id")) {
                        step.setNpcId(s.getString("npc_id"));
                    }
                    if (s.contains("reward_item")) {
                        step.setRewardItem(deserializeItem(s.getString("reward_item")));
                    }
                    if (s.isList("reward_commands")) {
                        step.setRewardCommands(s.getStringList("reward_commands"));
                    }

                    hunt.addStep(step);
                }
            }

            hunts.put(id.toLowerCase(), hunt);
        }
    }

    private String serializeItem(ItemStack item) {
        if (item == null) {
            return null;
        }
        return java.util.Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private ItemStack deserializeItem(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(base64);
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            return null;
        }
    }
}
