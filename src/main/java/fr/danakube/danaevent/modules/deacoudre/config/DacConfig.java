package fr.danakube.danaevent.modules.deacoudre.config;

import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameFormat;
import fr.danakube.danaevent.modules.deacoudre.model.DacTeamLifeMode;
import fr.danakube.danaevent.modules.deacoudre.model.JumpMode;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Handles loading, saving, and querying Dé à Coudre arenas from dac_arenas.yml.
 */
public class DacConfig {

    static {
        ConfigurationSerialization.registerClass(CuboidRegion.class, "CuboidRegion");
    }

    private final File configFile;
    private final Map<String, DacArena> arenas = new LinkedHashMap<>();

    public DacConfig(@NotNull JavaPlugin plugin) {
        this(new File(Objects.requireNonNull(plugin, "plugin cannot be null").getDataFolder(), "modules/deacoudre/dac_arenas.yml"));
    }

    public DacConfig(@NotNull File configFile) {
        this.configFile = Objects.requireNonNull(configFile, "configFile cannot be null");
    }

    public @NotNull File getConfigFile() {
        return configFile;
    }

    public @NotNull Collection<DacArena> getArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public Optional<DacArena> getArena(@Nullable String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(arenas.get(id.trim().toLowerCase()));
    }

    public void registerArena(@NotNull DacArena arena) {
        Objects.requireNonNull(arena, "arena cannot be null");
        arenas.put(arena.getId().trim().toLowerCase(), arena);
    }

    public DacArena createArena(
        @NotNull String id,
        @NotNull String displayName,
        @NotNull DacGameFormat format,
        @NotNull JumpMode jumpMode
    ) {
        String cleanId = Objects.requireNonNull(id, "id cannot be null").trim().toLowerCase();
        if (arenas.containsKey(cleanId)) {
            throw new IllegalArgumentException("Arena with ID '" + cleanId + "' already exists");
        }
        DacArena arena = new DacArena(cleanId, displayName, format, jumpMode);
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
     * Saves all in-memory arenas to dac_arenas.yml.
     */
    public void saveArenas() {
        YamlConfiguration config = new YamlConfiguration();

        for (DacArena arena : arenas.values()) {
            String path = "arenas." + arena.getId();
            config.set(path + ".display_name", arena.getDisplayName());
            config.set(path + ".format", arena.getFormat().name());
            config.set(path + ".jump_mode", arena.getJumpMode().name());
            config.set(path + ".team_life_mode", arena.getTeamLifeMode().name());
            config.set(path + ".initial_lives", arena.getInitialLives());
            config.set(path + ".jump_time_seconds", arena.getJumpTimeSeconds());
            config.set(path + ".enabled", arena.isEnabled());

            if (arena.getPoolRegion() != null) {
                config.set(path + ".pool_region", arena.getPoolRegion());
            }

            if (arena.getDivingLocation() != null) {
                config.set(path + ".diving_location", arena.getDivingLocation());
            }

            if (arena.getLobbyLocation() != null) {
                config.set(path + ".lobby_location", arena.getLobbyLocation());
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
     * Loads arenas from dac_arenas.yml into memory.
     */
    public void loadArenas() {
        arenas.clear();

        if (!configFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection arenasSection = config.getConfigurationSection("arenas");
        if (arenasSection == null) {
            return;
        }

        for (String id : arenasSection.getKeys(false)) {
            ConfigurationSection sec = arenasSection.getConfigurationSection(id);
            if (sec == null) {
                continue;
            }

            String displayName = sec.getString("display_name", id);
            DacGameFormat format = DacGameFormat.fromString(sec.getString("format", "SOLO"));
            JumpMode jumpMode = JumpMode.fromString(sec.getString("jump_mode", "TURN_BY_TURN"));
            DacTeamLifeMode teamLifeMode = DacTeamLifeMode.fromString(sec.getString("team_life_mode", "LAST_STANDING"));

            DacArena arena = new DacArena(id, displayName, format, jumpMode);
            arena.setTeamLifeMode(teamLifeMode);
            arena.setInitialLives(sec.getInt("initial_lives", 3));
            arena.setJumpTimeSeconds(sec.getInt("jump_time_seconds", 15));
            arena.setEnabled(sec.getBoolean("enabled", true));

            Object poolObj = sec.get("pool_region");
            if (poolObj instanceof CuboidRegion region) {
                arena.setPoolRegion(region);
            }

            Object divingObj = sec.get("diving_location");
            if (divingObj instanceof Location loc) {
                arena.setDivingLocation(loc);
            }

            Object lobbyObj = sec.get("lobby_location");
            if (lobbyObj instanceof Location loc) {
                arena.setLobbyLocation(loc);
            }

            arenas.put(arena.getId(), arena);
        }
    }
}
