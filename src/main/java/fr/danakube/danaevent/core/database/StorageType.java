package fr.danakube.danaevent.core.database;

/**
 * Supported storage types for the database manager.
 */
public enum StorageType {
    SQLITE,
    MYSQL,
    MARIADB;

    /**
     * Resolves a storage type from a case-insensitive string.
     * Defaults to {@link #SQLITE} if unrecognized or null.
     *
     * @param type the type name string
     * @return the resolved {@link StorageType}
     */
    public static StorageType fromString(String type) {
        if (type == null) {
            return SQLITE;
        }
        try {
            return StorageType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SQLITE;
        }
    }
}
