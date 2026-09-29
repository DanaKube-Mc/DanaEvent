package fr.danakube.danaevent.core.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseConfig config = new DatabaseConfig(
            StorageType.SQLITE,
            "localhost",
            3306,
            "test_database.db",
            "",
            "",
            5
        );
        databaseManager = new DatabaseManager(config, tempDir.toFile());
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null && databaseManager.isRunning()) {
            databaseManager.shutdown();
        }
    }

    @Test
    @DisplayName("HikariCP pool should initialize and connect properly")
    void shouldInitializeHikariPool() {
        assertThat(databaseManager.isRunning()).isTrue();
        assertThat(databaseManager.getDataSource()).isNotNull();
        assertThat(databaseManager.getDataSource().isClosed()).isFalse();
        assertThat(databaseManager.getStorageProvider()).isInstanceOf(SQLiteStorageProvider.class);
    }

    @Test
    @DisplayName("Table initialization should be idempotent without throwing errors on subsequent calls")
    void shouldBeIdempotentOnTableInitialization() throws SQLException {
        assertThat(databaseManager.isRunning()).isTrue();

        // Initial tables were created in constructor; calling initTables again must succeed cleanly
        databaseManager.initTables();
        databaseManager.initTables();

        assertThat(databaseManager.isRunning()).isTrue();
    }

    @Test
    @DisplayName("Should save and load player username correctly")
    void shouldSaveAndLoadPlayer() throws SQLException {
        UUID uuid = UUID.randomUUID();
        String username = "DamienDev";

        databaseManager.savePlayer(uuid, username);

        Optional<String> loaded = databaseManager.loadPlayer(uuid);
        assertThat(loaded).isPresent().contains(username);
    }

    @Test
    @DisplayName("Should update existing player username on duplicate save (upsert)")
    void shouldUpsertPlayerUsername() throws SQLException {
        UUID uuid = UUID.randomUUID();

        databaseManager.savePlayer(uuid, "InitialName");
        Optional<String> loadedFirst = databaseManager.loadPlayer(uuid);
        assertThat(loadedFirst).contains("InitialName");

        databaseManager.savePlayer(uuid, "UpdatedName");
        Optional<String> loadedSecond = databaseManager.loadPlayer(uuid);
        assertThat(loadedSecond).contains("UpdatedName");
    }

    @Test
    @DisplayName("Should return empty optional when loading non-existent player")
    void shouldReturnEmptyForUnknownPlayer() throws SQLException {
        UUID unknownUuid = UUID.randomUUID();

        Optional<String> loaded = databaseManager.loadPlayer(unknownUuid);
        assertThat(loaded).isEmpty();
    }

    @Test
    @DisplayName("Should save, load, and delete player snapshot byte data")
    void shouldHandleSnapshotLifecycle() throws SQLException {
        UUID uuid = UUID.randomUUID();
        byte[] snapshotData = "nbt-inventory-test-data-1.21".getBytes(StandardCharsets.UTF_8);

        // 1. Initially empty
        assertThat(databaseManager.loadSnapshot(uuid)).isEmpty();

        // 2. Save snapshot
        databaseManager.saveSnapshot(uuid, snapshotData);
        Optional<byte[]> loaded = databaseManager.loadSnapshot(uuid);
        assertThat(loaded).isPresent();
        assertThat(loaded.get()).isEqualTo(snapshotData);

        // 3. Upsert snapshot
        byte[] updatedData = "updated-nbt-inventory-test-data".getBytes(StandardCharsets.UTF_8);
        databaseManager.saveSnapshot(uuid, updatedData);
        Optional<byte[]> updatedLoaded = databaseManager.loadSnapshot(uuid);
        assertThat(updatedLoaded).isPresent();
        assertThat(updatedLoaded.get()).isEqualTo(updatedData);

        // 4. Delete snapshot
        databaseManager.deleteSnapshot(uuid);
        assertThat(databaseManager.loadSnapshot(uuid)).isEmpty();

        // 5. Deleting already deleted snapshot should not throw
        databaseManager.deleteSnapshot(uuid);
        assertThat(databaseManager.loadSnapshot(uuid)).isEmpty();
    }

    @Test
    @DisplayName("Should perform asynchronous player and snapshot operations without blocking")
    void shouldPerformAsyncOperations() throws ExecutionException, InterruptedException {
        UUID uuid = UUID.randomUUID();
        String username = "AsyncPlayer";
        byte[] snapshot = "async-snapshot".getBytes(StandardCharsets.UTF_8);

        // Save player async
        databaseManager.savePlayerAsync(uuid, username).join();
        Optional<String> loadedPlayer = databaseManager.loadPlayerAsync(uuid).join();
        assertThat(loadedPlayer).contains(username);

        // Save snapshot async
        databaseManager.saveSnapshotAsync(uuid, snapshot).join();
        Optional<byte[]> loadedSnapshot = databaseManager.loadSnapshotAsync(uuid).join();
        assertThat(loadedSnapshot).isPresent();
        assertThat(loadedSnapshot.get()).isEqualTo(snapshot);

        // Delete snapshot async
        databaseManager.deleteSnapshotAsync(uuid).join();
        Optional<byte[]> emptySnapshot = databaseManager.loadSnapshotAsync(uuid).join();
        assertThat(emptySnapshot).isEmpty();
    }

    @Test
    @DisplayName("Should cleanly shut down HikariCP pool and prevent subsequent operations")
    void shouldShutdownPoolCleanlyWithoutLeaks() {
        assertThat(databaseManager.isRunning()).isTrue();

        databaseManager.shutdown();

        assertThat(databaseManager.isRunning()).isFalse();
        assertThat(databaseManager.getDataSource().isClosed()).isTrue();

        UUID uuid = UUID.randomUUID();
        assertThatThrownBy(() -> databaseManager.savePlayer(uuid, "Test"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("closed");

        assertThatThrownBy(() -> databaseManager.loadPlayer(uuid))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("closed");
    }
}
