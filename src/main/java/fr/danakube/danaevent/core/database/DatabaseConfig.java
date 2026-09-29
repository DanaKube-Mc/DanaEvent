package fr.danakube.danaevent.core.database;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

/**
 * Configuration holder for database connection and connection pool settings.
 *
 * @param type     the storage type (SQLITE, MYSQL, MARIADB)
 * @param host     the database host address (for remote databases)
 * @param port     the database port (for remote databases)
 * @param database the database name or SQLite file path
 * @param username the authentication username
 * @param password the authentication password
 * @param poolSize the maximum connection pool size
 */
public record DatabaseConfig(
    StorageType type,
    String host,
    int port,
    String database,
    String username,
    String password,
    int poolSize
) {
    public DatabaseConfig {
        Objects.requireNonNull(type, "StorageType cannot be null");
        Objects.requireNonNull(host, "Host cannot be null");
        Objects.requireNonNull(database, "Database cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(password, "Password cannot be null");
        if (poolSize <= 0) {
            poolSize = 10;
        }
    }

    /**
     * Creates a default SQLite configuration.
     *
     * @param databaseName the name or relative path of the database file
     * @return the default SQLite DatabaseConfig
     */
    public static DatabaseConfig defaultSqlite(String databaseName) {
        return new DatabaseConfig(
            StorageType.SQLITE,
            "localhost",
            3306,
            databaseName != null ? databaseName : "danaevent.db",
            "",
            "",
            10
        );
    }

    /**
     * Parses a {@link DatabaseConfig} from a Bukkit {@link ConfigurationSection}.
     *
     * @param section       the configuration section, can be null
     * @param defaultDbName default database name if none is configured
     * @return the parsed DatabaseConfig or default SQLite configuration
     */
    public static DatabaseConfig fromConfiguration(ConfigurationSection section, String defaultDbName) {
        if (section == null) {
            return defaultSqlite(defaultDbName);
        }

        StorageType type = StorageType.fromString(section.getString("type", "SQLITE"));
        String host = section.getString("host", "localhost");
        int port = section.getInt("port", 3306);
        String database = section.getString("database", defaultDbName != null ? defaultDbName : "danaevent.db");
        String username = section.getString("username", "root");
        String password = section.getString("password", "");
        int poolSize = section.getInt("pool-size", 10);

        return new DatabaseConfig(type, host, port, database, username, password, poolSize);
    }
}
