package fr.danakube.danaevent.modules.boatrace.manager;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.database.DatabaseConfig;
import fr.danakube.danaevent.core.database.DatabaseManager;
import fr.danakube.danaevent.core.database.StorageType;
import fr.danakube.danaevent.modules.boatrace.database.BoatRaceDatabase;
import fr.danakube.danaevent.modules.boatrace.model.BoatSkin;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class BoatSkinManagerTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private DanaEventPlugin plugin;
    private DatabaseManager databaseManager;
    private BoatRaceDatabase boatRaceDatabase;
    private BoatSkinManager boatSkinManager;
    private File customBoatsFile;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);

        DatabaseConfig dbConfig = new DatabaseConfig(
            StorageType.SQLITE,
            "localhost",
            3306,
            "boat_skin_test.db",
            "",
            "",
            5
        );
        databaseManager = new DatabaseManager(dbConfig, tempDir.toFile());
        boatRaceDatabase = new BoatRaceDatabase(databaseManager);
        boatRaceDatabase.initTables();

        customBoatsFile = tempDir.resolve("boats.yml").toFile();
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
                permission: "danaevent.boat.birch"
              bamboo:
                material: BAMBOO_RAFT
                name: "<green>Radeau en Bambou</green>"
                permission: "danaevent.boat.vip"
            """;
        Files.writeString(customBoatsFile.toPath(), yaml);

        boatSkinManager = new BoatSkinManager(plugin, boatRaceDatabase, customBoatsFile);
        boatSkinManager.loadBoatsConfig();
    }

    @AfterEach
    void tearDown() {
        if (boatSkinManager != null) {
            boatSkinManager.cleanUp();
        }
        if (databaseManager != null && databaseManager.isRunning()) {
            databaseManager.shutdown();
        }
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should load available skins correctly from boats.yml")
    void shouldLoadAvailableSkinsFromConfig() {
        List<BoatSkin> skins = boatSkinManager.getAvailableSkins();
        assertThat(skins).hasSize(4);

        Optional<BoatSkin> oak = boatSkinManager.getSkinById("oak");
        assertThat(oak).isPresent();
        assertThat(oak.get().material()).isEqualTo(Material.OAK_BOAT);
        assertThat(oak.get().permission()).isEmpty();

        Optional<BoatSkin> bamboo = boatSkinManager.getSkinById("bamboo");
        assertThat(bamboo).isPresent();
        assertThat(bamboo.get().material()).isEqualTo(Material.BAMBOO_RAFT);
        assertThat(bamboo.get().permission()).isEqualTo("danaevent.boat.vip");
    }

    @Test
    @DisplayName("hasPreference should return false when no preference set, true when set")
    void shouldReportPreferenceState() {
        PlayerMock player = server.addPlayer("Alice");
        UUID uuid = player.getUniqueId();

        assertThat(boatSkinManager.hasPreference(uuid)).isFalse();

        boatSkinManager.setPreference(uuid, Material.SPRUCE_BOAT);

        assertThat(boatSkinManager.hasPreference(uuid)).isTrue();
        assertThat(boatSkinManager.getPreference(uuid)).isEqualTo(Material.SPRUCE_BOAT);
    }

    @Test
    @DisplayName("getPreferredBoat should return default first permitted boat if no preference")
    void shouldReturnDefaultBoatWhenNoPreference() {
        PlayerMock player = server.addPlayer("Bob");

        Material preferred = boatSkinManager.getPreferredBoat(player);
        assertThat(preferred).isEqualTo(Material.OAK_BOAT);
    }

    @Test
    @DisplayName("getPreferredBoat should respect player permission requirements")
    void shouldCheckPermissionsForPreferredBoat() {
        PlayerMock player = server.addPlayer("Charlie");

        // Player wants bamboo raft which requires "danaevent.boat.vip"
        boatSkinManager.setPreference(player.getUniqueId(), Material.BAMBOO_RAFT);

        // Charlie does not have VIP permission -> should fallback to first permitted boat (OAK_BOAT)
        assertThat(boatSkinManager.getPreferredBoat(player)).isEqualTo(Material.OAK_BOAT);

        // Grant VIP permission -> should return preferred BAMBOO_RAFT
        player.addAttachment(plugin, "danaevent.boat.vip", true);
        assertThat(boatSkinManager.getPreferredBoat(player)).isEqualTo(Material.BAMBOO_RAFT);
    }

    @Test
    @DisplayName("Should persist preference in database and reload into memory cache")
    void shouldPersistAndLoadFromDatabase() throws ExecutionException, InterruptedException {
        PlayerMock player = server.addPlayer("David");
        UUID uuid = player.getUniqueId();

        boatSkinManager.setPreference(uuid, Material.SPRUCE_BOAT).join();

        // Verify database holds it
        Optional<Material> dbPref = boatRaceDatabase.getPlayerBoatPreference(uuid).get();
        assertThat(dbPref).contains(Material.SPRUCE_BOAT);

        // Clear in-memory cache to simulate new server startup or player reconnect
        boatSkinManager.cleanUp();
        assertThat(boatSkinManager.hasPreference(uuid)).isFalse();

        // Reload from database
        Optional<Material> loaded = boatSkinManager.loadPlayerPreference(uuid).get();
        assertThat(loaded).contains(Material.SPRUCE_BOAT);
        assertThat(boatSkinManager.hasPreference(uuid)).isTrue();
        assertThat(boatSkinManager.getPreference(uuid)).isEqualTo(Material.SPRUCE_BOAT);
    }

    @Test
    @DisplayName("Skin permission check with null or empty permissions")
    void shouldHandleSkinPermissions() {
        PlayerMock player = server.addPlayer("Eve");

        BoatSkin openSkin = new BoatSkin("oak", Material.OAK_BOAT, "Oak", "");
        assertThat(openSkin.hasPermission(player)).isTrue();

        BoatSkin restrictedSkin = new BoatSkin("vip", Material.BAMBOO_RAFT, "VIP", "boat.vip");
        assertThat(restrictedSkin.hasPermission(player)).isFalse();

        player.addAttachment(plugin, "boat.vip", true);
        assertThat(restrictedSkin.hasPermission(player)).isTrue();

        assertThat(openSkin.hasPermission(null)).isFalse();
    }
}
