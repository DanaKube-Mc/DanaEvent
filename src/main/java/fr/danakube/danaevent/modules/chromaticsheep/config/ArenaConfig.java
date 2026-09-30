package fr.danakube.danaevent.modules.chromaticsheep.config;

import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameFormat;
import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepArena;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Handles loading, saving, and querying ChromaticSheep arenas from arenas.yml.
 */
public class ArenaConfig {

    static {
        ConfigurationSerialization.registerClass(CuboidRegion.class, "CuboidRegion");
    }

    private final File configFile;
    private final Map<String, SheepArena> arenas = new LinkedHashMap<>();

    public ArenaConfig(@NotNull JavaPlugin plugin) {
        this(new File(Objects.requireNonNull(plugin, "plugin cannot be null").getDataFolder(), "modules/chromaticsheep/arenas.yml"));
    }

    public ArenaConfig(@NotNull File configFile) {
        this.configFile = Objects.requireNonNull(configFile, "configFile cannot be null");
    }

    public @NotNull File getConfigFile() {
        return configFile;
    }

    public @NotNull Collection<SheepArena> getArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public Optional<SheepArena> getArena(@Nullable String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(arenas.get(id.trim().toLowerCase()));
    }

    public void registerArena(@NotNull SheepArena arena) {
        Objects.requireNonNull(arena, "arena cannot be null");
        arenas.put(arena.getId().trim().toLowerCase(), arena);
    }

    public SheepArena createArena(
        @NotNull String id,
        @NotNull String displayName,
        @NotNull GameFormat format,
        @NotNull ScoringMode scoringMode
    ) {
        String cleanId = Objects.requireNonNull(id, "id cannot be null").trim().toLowerCase();
        if (arenas.containsKey(cleanId)) {
            throw new IllegalArgumentException("Arena with ID '" + cleanId + "' already exists");
        }
        SheepArena arena = new SheepArena(cleanId, displayName, format, scoringMode);
        arenas.put(cleanId, arena);
        return arena;
    }

    public boolean deleteArena(@Nullable String id) {
        if (id == null) {
            return false;
        }
        return arenas.remove(id.trim().toLowerCase()) != null;
    }

    public void clear() {
        arenas.clear();
    }

    /**
     * Saves all in-memory arenas to arenas.yml.
     */
    public void saveArenas() {
        YamlConfiguration config = new YamlConfiguration();

        for (SheepArena arena : arenas.values()) {
            String path = "arenas." + arena.getId();
            config.set(path + ".display_name", arena.getDisplayName());
            config.set(path + ".format", arena.getFormat().name());
            config.set(path + ".scoring_mode", arena.getScoringMode().name());
            config.set(path + ".sheep_count", arena.getSheepCount());
            config.set(path + ".duration_seconds", arena.getDurationSeconds());
            config.set(path + ".enabled", arena.isEnabled());

            if (arena.getBounds() != null) {
                config.set(path + ".bounds", arena.getBounds());
            }

            if (!arena.getPlayerSpawns().isEmpty()) {
                config.set(path + ".player_spawns", arena.getPlayerSpawns());
            }
        }

        if (configFile.getParentFile() != null && !configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save arenas to " + configFile.getAbsolutePath(), e);
        }
    }

    /**
     * Loads arenas from arenas.yml.
     */
    public void loadArenas() {
        arenas.clear();

        if (!configFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection rootSection = config.getConfigurationSection("arenas");
        if (rootSection == null) {
            return;
        }

        for (String id : rootSection.getKeys(false)) {
            ConfigurationSection section = rootSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }

            String displayName = section.getString("display_name", id);
            GameFormat format = GameFormat.fromString(section.getString("format", "SOLO"));
            ScoringMode scoringMode = ScoringMode.fromString(section.getString("scoring_mode", "FINAL_COUNT"));
            int sheepCount = section.getInt("sheep_count", 40);
            int durationSeconds = section.getInt("duration_seconds", 120);
            boolean enabled = section.getBoolean("enabled", true);

            SheepArena arena = new SheepArena(id, displayName, format, scoringMode);
            arena.setSheepCount(sheepCount);
            arena.setDurationSeconds(durationSeconds);
            arena.setEnabled(enabled);

            Object boundsObj = section.get("bounds");
            if (boundsObj instanceof CuboidRegion bounds) {
                arena.setBounds(bounds);
            }

            List<?> spawnsList = section.getList("player_spawns");
            if (spawnsList != null) {
                for (Object item : spawnsList) {
                    if (item instanceof Location loc) {
                        arena.addPlayerSpawn(loc);
                    }
                }
            }

            arenas.put(id.trim().toLowerCase(), arena);
        }
    }
}
