package fr.danakube.danaevent.core.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * MySQL/MariaDB implementation of {@link StorageProvider}.
 */
public class MySQLStorageProvider implements StorageProvider {

    private final DataSource dataSource;

    public MySQLStorageProvider(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource cannot be null");
    }

    @Override
    public void initTables() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS dana_players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    username VARCHAR(16) NOT NULL,
                    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                );
            """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS dana_player_snapshots (
                    uuid VARCHAR(36) PRIMARY KEY,
                    data LONGBLOB NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                );
            """);
        }
    }

    @Override
    public void savePlayer(UUID uuid, String name) throws SQLException {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        Objects.requireNonNull(name, "Username cannot be null");

        String sql = """
            INSERT INTO dana_players (uuid, username, last_seen)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                username = VALUES(username),
                last_seen = CURRENT_TIMESTAMP;
        """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<String> loadPlayer(UUID uuid) throws SQLException {
        Objects.requireNonNull(uuid, "UUID cannot be null");

        String sql = "SELECT username FROM dana_players WHERE uuid = ?;";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.ofNullable(resultSet.getString("username"));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void saveSnapshot(UUID uuid, byte[] data) throws SQLException {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        Objects.requireNonNull(data, "Data cannot be null");

        String sql = """
            INSERT INTO dana_player_snapshots (uuid, data, created_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                data = VALUES(data),
                created_at = CURRENT_TIMESTAMP;
        """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setBytes(2, data);
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<byte[]> loadSnapshot(UUID uuid) throws SQLException {
        Objects.requireNonNull(uuid, "UUID cannot be null");

        String sql = "SELECT data FROM dana_player_snapshots WHERE uuid = ?;";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.ofNullable(resultSet.getBytes("data"));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void deleteSnapshot(UUID uuid) throws SQLException {
        Objects.requireNonNull(uuid, "UUID cannot be null");

        String sql = "DELETE FROM dana_player_snapshots WHERE uuid = ?;";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        }
    }
}
