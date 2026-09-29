package fr.danakube.danaevent.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Manages the database connection pool (HikariCP) and delegates persistence operations
 * to the appropriate {@link StorageProvider}.
 */
public class DatabaseManager {

    private final DatabaseConfig config;
    private final HikariDataSource dataSource;
    private final StorageProvider storageProvider;
    private final Executor asyncExecutor;

    public DatabaseManager(DatabaseConfig config) throws SQLException {
        this(config, null, ForkJoinPool.commonPool());
    }

    public DatabaseManager(DatabaseConfig config, File dataFolder) throws SQLException {
        this(config, dataFolder, ForkJoinPool.commonPool());
    }

    public DatabaseManager(DatabaseConfig config, File dataFolder, Executor asyncExecutor) throws SQLException {
        this.config = Objects.requireNonNull(config, "DatabaseConfig cannot be null");
        this.asyncExecutor = asyncExecutor != null ? asyncExecutor : ForkJoinPool.commonPool();

        HikariConfig hikariConfig = createHikariConfig(config, dataFolder);
        this.dataSource = new HikariDataSource(hikariConfig);
        this.storageProvider = createStorageProvider(config.type(), this.dataSource);

        initTables();
    }

    private HikariConfig createHikariConfig(DatabaseConfig config, File dataFolder) {
        HikariConfig hikari = new HikariConfig();
        hikari.setMaximumPoolSize(config.poolSize());
        hikari.setConnectionTimeout(30000);
        hikari.setIdleTimeout(600000);
        hikari.setMaxLifetime(1800000);

        if (config.type() == StorageType.SQLITE) {
            hikari.setPoolName("DanaEvent-SQLitePool");
            hikari.setDriverClassName("org.sqlite.JDBC");

            String dbName = config.database();
            String url;
            if (":memory:".equalsIgnoreCase(dbName)) {
                url = "jdbc:sqlite:file:danaevent_mem?mode=memory&cache=shared";
            } else if (dbName.startsWith("file:")) {
                url = "jdbc:sqlite:" + dbName;
            } else {
                File dbFile;
                if (dataFolder != null) {
                    dbFile = new File(dataFolder, dbName.endsWith(".db") ? dbName : dbName + ".db");
                } else {
                    dbFile = new File(dbName.endsWith(".db") ? dbName : dbName + ".db");
                }
                if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
                    dbFile.getParentFile().mkdirs();
                }
                url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            }

            hikari.setJdbcUrl(url);
            hikari.setConnectionTestQuery("SELECT 1");
        } else {
            hikari.setPoolName("DanaEvent-MySQLPool");
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            String url = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8",
                config.host(),
                config.port(),
                config.database()
            );
            hikari.setJdbcUrl(url);
            hikari.setUsername(config.username());
            hikari.setPassword(config.password());

            // MySQL performance optimizations
            hikari.addDataSourceProperty("cachePrepStmts", "true");
            hikari.addDataSourceProperty("prepStmtCacheSize", "250");
            hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikari.addDataSourceProperty("useServerPrepStmts", "true");
            hikari.addDataSourceProperty("useLocalSessionState", "true");
            hikari.addDataSourceProperty("rewriteBatchedStatements", "true");
            hikari.addDataSourceProperty("cacheResultSetMetadata", "true");
            hikari.addDataSourceProperty("cacheServerConfiguration", "true");
            hikari.addDataSourceProperty("elideSetAutoCommits", "true");
            hikari.addDataSourceProperty("maintainTimeStats", "false");
        }

        return hikari;
    }

    private StorageProvider createStorageProvider(StorageType type, HikariDataSource dataSource) {
        return switch (type) {
            case SQLITE -> new SQLiteStorageProvider(dataSource);
            case MYSQL, MARIADB -> new MySQLStorageProvider(dataSource);
        };
    }

    /**
     * Initializes database tables via the underlying storage provider.
     *
     * @throws SQLException if an error occurs during table creation
     */
    public void initTables() throws SQLException {
        ensureRunning();
        storageProvider.initTables();
    }

    /**
     * Checks if the connection pool is open and running.
     *
     * @return true if running, false if closed or null
     */
    public boolean isRunning() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Gracefully shuts down the HikariCP connection pool.
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public StorageProvider getStorageProvider() {
        return storageProvider;
    }

    public DatabaseConfig getConfig() {
        return config;
    }

    // --- Synchronous Delegation Methods ---

    public void savePlayer(UUID uuid, String name) throws SQLException {
        ensureRunning();
        storageProvider.savePlayer(uuid, name);
    }

    public Optional<String> loadPlayer(UUID uuid) throws SQLException {
        ensureRunning();
        return storageProvider.loadPlayer(uuid);
    }

    public void saveSnapshot(UUID uuid, byte[] data) throws SQLException {
        ensureRunning();
        storageProvider.saveSnapshot(uuid, data);
    }

    public Optional<byte[]> loadSnapshot(UUID uuid) throws SQLException {
        ensureRunning();
        return storageProvider.loadSnapshot(uuid);
    }

    public void deleteSnapshot(UUID uuid) throws SQLException {
        ensureRunning();
        storageProvider.deleteSnapshot(uuid);
    }

    // --- Asynchronous API (Non-blocking for Minecraft tick thread) ---

    public CompletableFuture<Void> savePlayerAsync(UUID uuid, String name) {
        return CompletableFuture.runAsync(() -> {
            try {
                savePlayer(uuid, name);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<Optional<String>> loadPlayerAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadPlayer(uuid);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<Void> saveSnapshotAsync(UUID uuid, byte[] data) {
        return CompletableFuture.runAsync(() -> {
            try {
                saveSnapshot(uuid, data);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<Optional<byte[]>> loadSnapshotAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadSnapshot(uuid);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<Void> deleteSnapshotAsync(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try {
                deleteSnapshot(uuid);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    private void ensureRunning() {
        if (!isRunning()) {
            throw new IllegalStateException("DatabaseManager is closed or uninitialized.");
        }
    }
}
