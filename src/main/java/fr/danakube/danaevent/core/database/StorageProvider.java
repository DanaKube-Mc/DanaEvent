package fr.danakube.danaevent.core.database;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage provider interface defining low-level persistence operations.
 */
public interface StorageProvider {

    /**
     * Initializes required database tables if they do not exist.
     *
     * @throws SQLException if a database access error occurs
     */
    void initTables() throws SQLException;

    /**
     * Saves or updates a player's record (UUID, username, and last seen timestamp).
     *
     * @param uuid the player's unique identifier
     * @param name the player's username
     * @throws SQLException if a database access error occurs
     */
    void savePlayer(UUID uuid, String name) throws SQLException;

    /**
     * Loads the last known username for a given player UUID.
     *
     * @param uuid the player's unique identifier
     * @return an {@link Optional} containing the username if found, or empty otherwise
     * @throws SQLException if a database access error occurs
     */
    Optional<String> loadPlayer(UUID uuid) throws SQLException;

    /**
     * Saves or updates a serialized state snapshot for a player.
     *
     * @param uuid the player's unique identifier
     * @param data the serialized snapshot byte array
     * @throws SQLException if a database access error occurs
     */
    void saveSnapshot(UUID uuid, byte[] data) throws SQLException;

    /**
     * Loads the serialized state snapshot for a player.
     *
     * @param uuid the player's unique identifier
     * @return an {@link Optional} containing the snapshot byte array if found, or empty otherwise
     * @throws SQLException if a database access error occurs
     */
    Optional<byte[]> loadSnapshot(UUID uuid) throws SQLException;

    /**
     * Deletes a player's state snapshot from storage.
     *
     * @param uuid the player's unique identifier
     * @throws SQLException if a database access error occurs
     */
    void deleteSnapshot(UUID uuid) throws SQLException;
}
