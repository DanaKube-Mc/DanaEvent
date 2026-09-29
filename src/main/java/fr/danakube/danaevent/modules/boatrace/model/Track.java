package fr.danakube.danaevent.modules.boatrace.model;

import fr.danakube.danaevent.core.selection.CuboidRegion;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a BoatRace track configuration.
 */
public class Track {

    private final String id;
    private String name;
    private TrackType type;
    private TrackMode mode;
    private CuboidRegion startRegion;
    private CuboidRegion finishRegion;
    private final List<Location> spawnPoints = new ArrayList<>();
    private Location lobbyLocation;
    private boolean collisionsEnabled;
    private Material boatMaterial;
    private int laps;

    /**
     * Constructs a new Track with default configuration.
     *
     * @param id unique track identifier
     * @param name track display name
     * @param type track race type
     * @param mode track operating mode
     */
    public Track(String id, String name, TrackType type, TrackMode mode) {
        this.id = Objects.requireNonNull(id, "Track id cannot be null");
        this.name = Objects.requireNonNull(name, "Track name cannot be null");
        this.type = Objects.requireNonNull(type, "Track type cannot be null");
        this.mode = Objects.requireNonNull(mode, "Track mode cannot be null");
        this.collisionsEnabled = false;
        this.boatMaterial = Material.OAK_BOAT;
        this.laps = 1;
    }

    /**
     * Checks if the track is completely configured and ready for racing.
     * Requires startRegion, finishRegion and at least one spawn point.
     *
     * @return true if ready, false otherwise
     */
    public boolean isReady() {
        return startRegion != null && finishRegion != null && !spawnPoints.isEmpty();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "Track name cannot be null");
    }

    public TrackType getType() {
        return type;
    }

    public void setType(TrackType type) {
        this.type = Objects.requireNonNull(type, "Track type cannot be null");
    }

    public TrackMode getMode() {
        return mode;
    }

    public void setMode(TrackMode mode) {
        this.mode = Objects.requireNonNull(mode, "Track mode cannot be null");
    }

    public CuboidRegion getStartRegion() {
        return startRegion;
    }

    public void setStartRegion(CuboidRegion startRegion) {
        this.startRegion = startRegion;
    }

    public CuboidRegion getFinishRegion() {
        return finishRegion;
    }

    public void setFinishRegion(CuboidRegion finishRegion) {
        this.finishRegion = finishRegion;
    }

    public List<Location> getSpawnPoints() {
        return Collections.unmodifiableList(spawnPoints);
    }

    public void setSpawnPoints(List<Location> locations) {
        this.spawnPoints.clear();
        if (locations != null) {
            for (Location loc : locations) {
                if (loc != null) {
                    this.spawnPoints.add(loc.clone());
                }
            }
        }
    }

    public void addSpawnPoint(Location location) {
        if (location != null) {
            this.spawnPoints.add(location.clone());
        }
    }

    public boolean removeSpawnPoint(Location location) {
        return this.spawnPoints.remove(location);
    }

    public void clearSpawnPoints() {
        this.spawnPoints.clear();
    }

    public Location getLobbyLocation() {
        return lobbyLocation != null ? lobbyLocation.clone() : null;
    }

    public void setLobbyLocation(Location lobbyLocation) {
        this.lobbyLocation = lobbyLocation != null ? lobbyLocation.clone() : null;
    }

    public boolean isCollisionsEnabled() {
        return collisionsEnabled;
    }

    public void setCollisionsEnabled(boolean collisionsEnabled) {
        this.collisionsEnabled = collisionsEnabled;
    }

    public Material getBoatMaterial() {
        return boatMaterial;
    }

    public void setBoatMaterial(Material boatMaterial) {
        this.boatMaterial = Objects.requireNonNull(boatMaterial, "Boat material cannot be null");
    }

    public int getLaps() {
        return laps;
    }

    public void setLaps(int laps) {
        if (laps < 1) {
            throw new IllegalArgumentException("Laps must be at least 1");
        }
        this.laps = laps;
    }
}
