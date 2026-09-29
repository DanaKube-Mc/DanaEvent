package fr.danakube.danaevent.modules.boatrace.manager;

import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.boatrace.model.Track;
import fr.danakube.danaevent.modules.boatrace.model.TrackMode;
import fr.danakube.danaevent.modules.boatrace.model.TrackType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;

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
 * Manages BoatRace tracks and handles YAML persistence in plugins/DanaEvent/modules/boatrace/tracks.yml.
 */
public class TrackManager {

    static {
        ConfigurationSerialization.registerClass(CuboidRegion.class, "CuboidRegion");
    }

    private final File configFile;
    private final Map<String, Track> tracks = new LinkedHashMap<>();

    /**
     * Constructs a TrackManager for the specified plugin, defaulting to
     * plugins/DanaEvent/modules/boatrace/tracks.yml.
     *
     * @param plugin the JavaPlugin instance
     */
    public TrackManager(JavaPlugin plugin) {
        this(new File(Objects.requireNonNull(plugin, "plugin cannot be null").getDataFolder(), "modules/boatrace/tracks.yml"));
    }

    /**
     * Constructs a TrackManager with a custom configuration file.
     *
     * @param configFile the YAML tracks file
     */
    public TrackManager(File configFile) {
        this.configFile = Objects.requireNonNull(configFile, "configFile cannot be null");
    }

    /**
     * Creates and registers a new track in memory.
     *
     * @param id unique track identifier
     * @param name track display name
     * @param type track race type
     * @param mode track operating mode
     * @return the created Track
     * @throws IllegalArgumentException if a track with the given ID already exists
     */
    public Track createTrack(String id, String name, TrackType type, TrackMode mode) {
        Objects.requireNonNull(id, "Track id cannot be null");
        Objects.requireNonNull(name, "Track name cannot be null");
        Objects.requireNonNull(type, "Track type cannot be null");
        Objects.requireNonNull(mode, "Track mode cannot be null");

        if (tracks.containsKey(id)) {
            throw new IllegalArgumentException("Track with ID '" + id + "' already exists");
        }

        Track track = new Track(id, name, type, mode);
        tracks.put(id, track);
        return track;
    }

    /**
     * Deletes a track by its identifier.
     *
     * @param id track identifier
     * @return true if the track was removed, false otherwise
     */
    public boolean deleteTrack(String id) {
        if (id == null) {
            return false;
        }
        return tracks.remove(id) != null;
    }

    /**
     * Retrieves a track by ID.
     *
     * @param id track identifier
     * @return Optional containing track if present
     */
    public Optional<Track> getTrack(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tracks.get(id));
    }

    /**
     * @return an unmodifiable collection of all loaded tracks
     */
    public Collection<Track> getTracks() {
        return Collections.unmodifiableCollection(tracks.values());
    }

    /**
     * Saves all tracks into the YAML configuration file.
     */
    public void saveTracks() {
        YamlConfiguration config = new YamlConfiguration();

        for (Track track : tracks.values()) {
            String path = "tracks." + track.getId();
            config.set(path + ".name", track.getName());
            config.set(path + ".type", track.getType().name());
            config.set(path + ".mode", track.getMode().name());
            config.set(path + ".collisions", track.isCollisionsEnabled());
            config.set(path + ".boat_material", track.getBoatMaterial().name());
            config.set(path + ".laps", track.getLaps());

            if (track.getLobbyLocation() != null) {
                config.set(path + ".lobby", track.getLobbyLocation());
            }

            if (track.getStartRegion() != null) {
                config.set(path + ".start_region", track.getStartRegion());
            }

            if (track.getFinishRegion() != null) {
                config.set(path + ".finish_region", track.getFinishRegion());
            }

            if (!track.getSpawnPoints().isEmpty()) {
                config.set(path + ".spawn_points", track.getSpawnPoints());
            }
        }

        if (configFile.getParentFile() != null && !configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save tracks configuration to " + configFile.getAbsolutePath(), e);
        }
    }

    /**
     * Loads tracks from the YAML configuration file.
     */
    public void loadTracks() {
        tracks.clear();

        if (!configFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection tracksSection = config.getConfigurationSection("tracks");
        if (tracksSection == null) {
            return;
        }

        for (String id : tracksSection.getKeys(false)) {
            ConfigurationSection section = tracksSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }

            String name = section.getString("name", id);
            TrackType type = TrackType.valueOf(section.getString("type", TrackType.SPRINT.name()));
            TrackMode mode = TrackMode.valueOf(section.getString("mode", TrackMode.TIME_ATTACK_247.name()));

            Track track = new Track(id, name, type, mode);
            track.setCollisionsEnabled(section.getBoolean("collisions", false));

            String matName = section.getString("boat_material", Material.OAK_BOAT.name());
            Material mat = Material.getMaterial(matName);
            track.setBoatMaterial(mat != null ? mat : Material.OAK_BOAT);

            track.setLaps(Math.max(1, section.getInt("laps", 1)));

            Location lobby = section.getLocation("lobby");
            if (lobby == null && section.get("lobby") instanceof Location loc) {
                lobby = loc;
            }
            track.setLobbyLocation(lobby);

            track.setStartRegion(parseRegion(section.get("start_region")));
            track.setFinishRegion(parseRegion(section.get("finish_region")));

            List<?> spawns = section.getList("spawn_points");
            if (spawns != null) {
                for (Object item : spawns) {
                    if (item instanceof Location loc) {
                        track.addSpawnPoint(loc);
                    } else if (item instanceof Map<?, ?> map) {
                        @SuppressWarnings("unchecked")
                        Location loc = Location.deserialize((Map<String, Object>) map);
                        track.addSpawnPoint(loc);
                    }
                }
            }

            tracks.put(id, track);
        }
    }

    private CuboidRegion parseRegion(Object obj) {
        if (obj instanceof CuboidRegion region) {
            return region;
        }
        if (obj instanceof ConfigurationSection section) {
            return new CuboidRegion(section.getValues(false));
        }
        if (obj instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) map;
            return new CuboidRegion(casted);
        }
        return null;
    }
}
