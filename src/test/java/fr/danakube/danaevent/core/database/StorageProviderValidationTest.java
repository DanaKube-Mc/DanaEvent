package fr.danakube.danaevent.core.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageProviderValidationTest {

    @Test
    @DisplayName("StorageProviders should throw NullPointerException on null DataSource")
    void shouldThrowOnNullDataSource() {
        assertThatThrownBy(() -> new SQLiteStorageProvider(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("DataSource");

        assertThatThrownBy(() -> new MySQLStorageProvider(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("DataSource");
    }

    @Test
    @DisplayName("MySQLStorageProvider should enforce null checks on all method parameters")
    void shouldEnforceNullChecksOnMySQLProvider() {
        DataSource dummyDs = new org.sqlite.SQLiteDataSource();
        MySQLStorageProvider provider = new MySQLStorageProvider(dummyDs);
        UUID uuid = UUID.randomUUID();

        assertThatThrownBy(() -> provider.savePlayer(null, "Test"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> provider.savePlayer(uuid, null))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> provider.loadPlayer(null))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> provider.saveSnapshot(null, new byte[0]))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> provider.saveSnapshot(uuid, null))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> provider.loadSnapshot(null))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> provider.deleteSnapshot(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("SQLiteStorageProvider should enforce null checks on all method parameters")
    void shouldEnforceNullChecksOnSQLiteProvider() {
        DataSource dummyDs = new org.sqlite.SQLiteDataSource();
        SQLiteStorageProvider provider = new SQLiteStorageProvider(dummyDs);
        UUID uuid = UUID.randomUUID();

        assertThatThrownBy(() -> provider.savePlayer(null, "Test"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> provider.savePlayer(uuid, null))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> provider.loadPlayer(null))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> provider.saveSnapshot(null, new byte[0]))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> provider.saveSnapshot(uuid, null))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> provider.loadSnapshot(null))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> provider.deleteSnapshot(null))
            .isInstanceOf(NullPointerException.class);
    }
}
