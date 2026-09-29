package fr.danakube.danaevent.core.database;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseConfigTest {

    @Test
    @DisplayName("Should create default SQLite configuration with valid parameters")
    void shouldCreateDefaultSqliteConfig() {
        DatabaseConfig config = DatabaseConfig.defaultSqlite("test.db");

        assertThat(config.type()).isEqualTo(StorageType.SQLITE);
        assertThat(config.database()).isEqualTo("test.db");
        assertThat(config.poolSize()).isEqualTo(10);
        assertThat(config.host()).isEqualTo("localhost");
    }

    @Test
    @DisplayName("Should parse configuration from Bukkit ConfigurationSection")
    void shouldParseFromYamlSection() {
        String yaml = """
            database:
              type: MYSQL
              host: "10.0.0.1"
              port: 3307
              database: "danadb"
              username: "danauser"
              password: "secretpassword"
              pool-size: 20
        """;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        DatabaseConfig dbConfig = DatabaseConfig.fromConfiguration(config.getConfigurationSection("database"), "fallback.db");

        assertThat(dbConfig.type()).isEqualTo(StorageType.MYSQL);
        assertThat(dbConfig.host()).isEqualTo("10.0.0.1");
        assertThat(dbConfig.port()).isEqualTo(3307);
        assertThat(dbConfig.database()).isEqualTo("danadb");
        assertThat(dbConfig.username()).isEqualTo("danauser");
        assertThat(dbConfig.password()).isEqualTo("secretpassword");
        assertThat(dbConfig.poolSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("Should fallback to default SQLite configuration when section is null")
    void shouldFallbackWhenSectionIsNull() {
        DatabaseConfig dbConfig = DatabaseConfig.fromConfiguration(null, "default.db");

        assertThat(dbConfig.type()).isEqualTo(StorageType.SQLITE);
        assertThat(dbConfig.database()).isEqualTo("default.db");
    }

    @Test
    @DisplayName("Should enforce non-null arguments in constructor")
    void shouldThrowOnNullArguments() {
        assertThatThrownBy(() -> new DatabaseConfig(null, "localhost", 3306, "db", "user", "pass", 10))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DatabaseConfig(StorageType.SQLITE, null, 3306, "db", "user", "pass", 10))
            .isInstanceOf(NullPointerException.class);
    }
}
