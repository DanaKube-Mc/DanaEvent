package fr.danakube.danaevent.modules.boatrace.manager;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.modules.boatrace.database.BoatRaceDatabase;
import fr.danakube.danaevent.modules.boatrace.model.BoatSkin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages cosmetic boat models configured in boats.yml, in-memory caching of player preferences,
 * and asynchronous synchronization with the database.
 */
public class BoatSkinManager {

    private final DanaEventPlugin plugin;
    private final BoatRaceDatabase database;
    private final File configFile;

    private final Map<String, BoatSkin> availableSkins = new LinkedHashMap<>();
    private final Map<UUID, Material> playerPreferences = new ConcurrentHashMap<>();

    public BoatSkinManager(@Nullable DanaEventPlugin plugin, @Nullable BoatRaceDatabase database) {
        this(plugin, database, plugin != null ? new File(plugin.getDataFolder(), "boats.yml") : new File("boats.yml"));
    }

    public BoatSkinManager(@Nullable DanaEventPlugin plugin, @Nullable BoatRaceDatabase database, @NotNull File configFile) {
        this.plugin = plugin;
        this.database = database;
        this.configFile = Objects.requireNonNull(configFile, "configFile cannot be null");
    }

    /**
     * Loads or reloads boat models from boats.yml.
     * Extracts default boats.yml from resources if missing on disk.
     */
    public void loadBoatsConfig() {
        availableSkins.clear();

        if (!configFile.exists()) {
            extractDefaultConfig();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection section = config.getConfigurationSection("boats");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection boatSec = section.getConfigurationSection(key);
                if (boatSec == null) {
                    continue;
                }

                String matStr = boatSec.getString("material");
                if (matStr == null || matStr.isBlank()) {
                    continue;
                }

                Material material = Material.matchMaterial(matStr);
                if (material == null) {
                    if (plugin != null) {
                        plugin.getLogger().log(Level.FINE, "Material '" + matStr + "' for boat '" + key + "' is not supported in this server version.");
                    }
                    continue;
                }

                String name = boatSec.getString("name", key);
                String permission = boatSec.getString("permission", "").trim();

                BoatSkin skin = new BoatSkin(key, material, name, permission);
                availableSkins.put(key, skin);
            }
        }

        // Fallback default oak boat if config is empty or invalid
        if (availableSkins.isEmpty()) {
            availableSkins.put("oak", new BoatSkin("oak", Material.OAK_BOAT, "<green>Bateau en Chêne</green>", ""));
        }
    }

    private void extractDefaultConfig() {
        try {
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            if (plugin != null) {
                try {
                    plugin.saveResource("boats.yml", false);
                    return;
                } catch (IllegalArgumentException | IllegalStateException ignored) {
                }
            }

            // Fallback: extract using classloader or write embedded defaults
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("boats.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    writeFallbackBoatsFile();
                }
            }
        } catch (IOException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.WARNING, "Failed to extract default boats.yml: " + e.getMessage(), e);
            }
        }
    }

    private void writeFallbackBoatsFile() throws IOException {
        String yaml = """
            boats:
              oak:
                material: OAK_BOAT
                name: "<green>Bateau en Chêne</green>"
                permission: ""
              spruce:
                material: SPRUCE_BOAT
                name: "<dark_green>Bateau en Sapin</dark_green>"
                permission: ""
              birch:
                material: BIRCH_BOAT
                name: "<yellow>Bateau en Bouleau</yellow>"
                permission: ""
              jungle:
                material: JUNGLE_BOAT
                name: "<gold>Bateau en Acajou</gold>"
                permission: ""
              acacia:
                material: ACACIA_BOAT
                name: "<red>Bateau en Acacia</red>"
                permission: ""
              dark_oak:
                material: DARK_OAK_BOAT
                name: "<dark_red>Bateau en Chêne Sombre</dark_red>"
                permission: ""
              mangrove:
                material: MANGROVE_BOAT
                name: "<color:#852b2b>Bateau en Palétuvier</color>"
                permission: ""
              cherry:
                material: CHERRY_BOAT
                name: "<light_purple>Bateau en Cerisier</light_purple>"
                permission: ""
              bamboo:
                material: BAMBOO_RAFT
                name: "<green>Radeau en Bambou</green>"
                permission: ""
            """;
        Files.writeString(configFile.toPath(), yaml);
    }

    /**
     * Checks if the given player has an active preference set in memory.
     *
     * @param uuid player unique identifier
     * @return true if a boat preference is present in cache
     */
    public boolean hasPreference(@Nullable UUID uuid) {
        return uuid != null && playerPreferences.containsKey(uuid) && playerPreferences.get(uuid) != null;
    }

    /**
     * Retrieves the preferred boat material for a player.
     * If no preference is memorized or if permission was revoked, returns an accessible boat fallback.
     *
     * @param player the player
     * @return preferred Material, or first permitted boat material, or OAK_BOAT
     */
    public @NotNull Material getPreferredBoat(@Nullable Player player) {
        if (player == null) {
            return getDefaultBoatMaterial();
        }

        Material preferred = playerPreferences.get(player.getUniqueId());
        if (preferred != null) {
            Optional<BoatSkin> skinOpt = getSkinByMaterial(preferred);
            if (skinOpt.isPresent() && skinOpt.get().hasPermission(player)) {
                return preferred;
            }
        }

        // Fallback to first available permitted boat
        for (BoatSkin skin : availableSkins.values()) {
            if (skin.hasPermission(player)) {
                return skin.material();
            }
        }

        return getDefaultBoatMaterial();
    }

    /**
     * Returns raw memorized preference for a UUID, or null if none.
     *
     * @param uuid player unique identifier
     * @return Material or null
     */
    public @Nullable Material getPreference(@Nullable UUID uuid) {
        return uuid != null ? playerPreferences.get(uuid) : null;
    }

    /**
     * Sets player boat preference in memory and persists asynchronously to database.
     *
     * @param uuid player UUID
     * @param material chosen Material
     * @return CompletableFuture completing when DB is updated
     */
    public CompletableFuture<Void> setPreference(@NotNull UUID uuid, @NotNull Material material) {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(material, "material cannot be null");

        playerPreferences.put(uuid, material);

        if (database != null) {
            return database.setPlayerBoatPreference(uuid, material);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Asynchronously loads preference from database into memory cache.
     *
     * @param uuid player UUID
     * @return CompletableFuture with loaded Material Optional
     */
    public CompletableFuture<Optional<Material>> loadPlayerPreference(@NotNull UUID uuid) {
        Objects.requireNonNull(uuid, "uuid cannot be null");

        if (database == null) {
            return CompletableFuture.completedFuture(Optional.ofNullable(playerPreferences.get(uuid)));
        }

        return database.getPlayerBoatPreference(uuid).thenApply(opt -> {
            opt.ifPresent(material -> playerPreferences.put(uuid, material));
            return opt;
        });
    }

    /**
     * Removes cached preference for UUID.
     *
     * @param uuid player UUID
     */
    public void invalidatePreference(@Nullable UUID uuid) {
        if (uuid != null) {
            playerPreferences.remove(uuid);
        }
    }

    /**
     * Clears all in-memory cached preferences.
     */
    public void cleanUp() {
        playerPreferences.clear();
    }

    public @NotNull List<BoatSkin> getAvailableSkins() {
        return List.copyOf(availableSkins.values());
    }

    public Optional<BoatSkin> getSkinByMaterial(@Nullable Material material) {
        if (material == null) {
            return Optional.empty();
        }
        return availableSkins.values().stream()
            .filter(skin -> skin.material() == material)
            .findFirst();
    }

    public Optional<BoatSkin> getSkinById(@Nullable String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(availableSkins.get(id));
    }

    private Material getDefaultBoatMaterial() {
        return availableSkins.values().stream()
            .findFirst()
            .map(BoatSkin::material)
            .orElse(Material.OAK_BOAT);
    }
}
