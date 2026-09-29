package fr.danakube.danaevent.core.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class StorageTypeTest {

    @ParameterizedTest
    @CsvSource({
        "sqlite, SQLITE",
        "SQLite, SQLITE",
        "SQLITE, SQLITE",
        "mysql, MYSQL",
        "MySQL, MYSQL",
        "mariadb, MARIADB",
        "MariaDB, MARIADB"
    })
    @DisplayName("Should resolve StorageType case-insensitively")
    void shouldResolveFromString(String input, StorageType expected) {
        assertThat(StorageType.fromString(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should default to SQLITE on null or invalid input")
    void shouldDefaultToSqliteOnUnknownInput() {
        assertThat(StorageType.fromString(null)).isEqualTo(StorageType.SQLITE);
        assertThat(StorageType.fromString("unknown_engine")).isEqualTo(StorageType.SQLITE);
        assertThat(StorageType.fromString("   ")).isEqualTo(StorageType.SQLITE);
    }
}
