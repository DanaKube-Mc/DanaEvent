package fr.danakube.danaevent.modules.boatrace.manager;

import fr.danakube.danaevent.modules.boatrace.database.BoatRaceDatabase;
import fr.danakube.danaevent.modules.boatrace.model.RecordEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages thread-safe in-memory caching and resolution for BoatRace leaderboards,
 * personal bests, and player pseudonyms without blocking the tick thread.
 */
public class BoatRaceLeaderboardManager {

    public static final long DEFAULT_CACHE_TTL_MILLIS = 30_000L;

    private static BoatRaceLeaderboardManager instance;

    private final JavaPlugin plugin;
    private final BoatRaceDatabase database;
    private final long cacheTtlMillis;

    private final Map<String, CachedRecords> monthlyCache = new ConcurrentHashMap<>();
    private final Map<String, CachedRecords> allTimeCache = new ConcurrentHashMap<>();
    private final Map<String, CachedPb> pbCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNameCache = new ConcurrentHashMap<>();

    public BoatRaceLeaderboardManager(BoatRaceDatabase database) {
        this(null, database, DEFAULT_CACHE_TTL_MILLIS);
    }

    public BoatRaceLeaderboardManager(JavaPlugin plugin, BoatRaceDatabase database) {
        this(plugin, database, DEFAULT_CACHE_TTL_MILLIS);
    }

    public BoatRaceLeaderboardManager(JavaPlugin plugin, BoatRaceDatabase database, long cacheTtlMillis) {
        this.plugin = plugin;
        this.database = Objects.requireNonNull(database, "database cannot be null");
        this.cacheTtlMillis = Math.max(1000L, cacheTtlMillis);
    }

    public static BoatRaceLeaderboardManager getInstance() {
        return instance;
    }

    public static void setInstance(BoatRaceLeaderboardManager inst) {
        instance = inst;
    }

    /**
     * Returns the current period month in 'YYYY-MM' format (e.g. '2026-09').
     *
     * @return current period string
     */
    public String getCurrentPeriodMonth() {
        return YearMonth.now().toString();
    }

    /**
     * Resolves a player's username by UUID using cache, Bukkit online/offline player,
     * or the database table `dana_players`.
     *
     * @param playerUuid UUID of the player
     * @return resolved player username, or "Inconnu" if unresolved
     */
    public String resolvePlayerName(UUID playerUuid) {
        if (playerUuid == null) {
            return "Inconnu";
        }
        String cached = playerNameCache.get(playerUuid);
        if (cached != null) {
            return cached;
        }

        try {
            Player online = Bukkit.getPlayer(playerUuid);
            if (online != null && online.getName() != null) {
                playerNameCache.put(playerUuid, online.getName());
                return online.getName();
            }

            OfflinePlayer offline = Bukkit.getOfflinePlayer(playerUuid);
            if (offline != null && offline.getName() != null) {
                playerNameCache.put(playerUuid, offline.getName());
                return offline.getName();
            }
        } catch (Throwable ignored) {
            // Bukkit may not be initialized in non-mock tests
        }

        if (database.getDatabaseManager() != null && database.getDatabaseManager().getStorageProvider() != null) {
            try {
                Optional<String> dbName = database.getDatabaseManager().getStorageProvider().loadPlayer(playerUuid);
                if (dbName.isPresent() && !dbName.get().isBlank()) {
                    playerNameCache.put(playerUuid, dbName.get());
                    return dbName.get();
                }
            } catch (Exception ignored) {
            }
        }

        return "Inconnu";
    }

    /**
     * Manually registers a known player username in the resolution cache.
     *
     * @param uuid player UUID
     * @param name username
     */
    public void registerPlayerName(UUID uuid, String name) {
        if (uuid != null && name != null && !name.isBlank()) {
            playerNameCache.put(uuid, name);
        }
    }

    /**
     * Asynchronously retrieves the top monthly records for a track.
     * Uses memory cache if fresh, otherwise loads from database.
     *
     * @param trackId track identifier
     * @param limit maximum records to return
     * @return CompletableFuture containing ordered list of top records
     */
    public CompletableFuture<List<RecordEntry>> getTopMonthly(String trackId, int limit) {
        return getTopMonthly(trackId, getCurrentPeriodMonth(), limit);
    }

    /**
     * Asynchronously retrieves the top monthly records for a track and specific period.
     *
     * @param trackId track identifier
     * @param periodMonth period key (e.g. '2026-09')
     * @param limit maximum records to return
     * @return CompletableFuture containing ordered list of top records
     */
    public CompletableFuture<List<RecordEntry>> getTopMonthly(String trackId, String periodMonth, int limit) {
        Objects.requireNonNull(trackId, "trackId cannot be null");
        Objects.requireNonNull(periodMonth, "periodMonth cannot be null");
        int effectiveLimit = Math.max(1, limit);
        String cacheKey = trackId + ":" + periodMonth;

        CachedRecords cached = monthlyCache.get(cacheKey);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return CompletableFuture.completedFuture(slice(cached.records(), effectiveLimit));
        }

        return database.getTopMonthly(trackId, periodMonth, Math.max(effectiveLimit, 50))
            .thenApply(records -> {
                monthlyCache.put(cacheKey, new CachedRecords(records, System.currentTimeMillis()));
                return slice(records, effectiveLimit);
            });
    }

    /**
     * Asynchronously retrieves the top all-time records for a track.
     * Uses memory cache if fresh, otherwise loads from database.
     *
     * @param trackId track identifier
     * @param limit maximum records to return
     * @return CompletableFuture containing ordered list of top records
     */
    public CompletableFuture<List<RecordEntry>> getTopAllTime(String trackId, int limit) {
        Objects.requireNonNull(trackId, "trackId cannot be null");
        int effectiveLimit = Math.max(1, limit);

        CachedRecords cached = allTimeCache.get(trackId);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return CompletableFuture.completedFuture(slice(cached.records(), effectiveLimit));
        }

        return database.getTopAllTime(trackId, Math.max(effectiveLimit, 50))
            .thenApply(records -> {
                allTimeCache.put(trackId, new CachedRecords(records, System.currentTimeMillis()));
                return slice(records, effectiveLimit);
            });
    }

    /**
     * Asynchronously retrieves the personal best (all-time) record for a player on a track.
     *
     * @param trackId track identifier
     * @param playerUuid player UUID
     * @return CompletableFuture containing Optional record
     */
    public CompletableFuture<Optional<RecordEntry>> getPersonalBest(String trackId, UUID playerUuid) {
        Objects.requireNonNull(trackId, "trackId cannot be null");
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        String cacheKey = trackId + ":" + playerUuid + ":ALL";

        CachedPb cached = pbCache.get(cacheKey);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return CompletableFuture.completedFuture(cached.record());
        }

        return database.getPersonalBest(trackId, playerUuid)
            .thenApply(opt -> {
                pbCache.put(cacheKey, new CachedPb(opt, System.currentTimeMillis()));
                return opt;
            });
    }

    /**
     * Asynchronously retrieves the current monthly personal best record for a player on a track.
     *
     * @param trackId track identifier
     * @param playerUuid player UUID
     * @return CompletableFuture containing Optional record
     */
    public CompletableFuture<Optional<RecordEntry>> getMonthlyPersonalBest(String trackId, UUID playerUuid) {
        return getMonthlyPersonalBest(trackId, playerUuid, getCurrentPeriodMonth());
    }

    /**
     * Asynchronously retrieves the monthly personal best record for a player on a track for a period.
     *
     * @param trackId track identifier
     * @param playerUuid player UUID
     * @param periodMonth month key (e.g. '2026-09')
     * @return CompletableFuture containing Optional record
     */
    public CompletableFuture<Optional<RecordEntry>> getMonthlyPersonalBest(String trackId, UUID playerUuid, String periodMonth) {
        Objects.requireNonNull(trackId, "trackId cannot be null");
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(periodMonth, "periodMonth cannot be null");
        String cacheKey = trackId + ":" + playerUuid + ":" + periodMonth;

        CachedPb cached = pbCache.get(cacheKey);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return CompletableFuture.completedFuture(cached.record());
        }

        return database.getMonthlyPersonalBest(trackId, playerUuid, periodMonth)
            .thenApply(opt -> {
                pbCache.put(cacheKey, new CachedPb(opt, System.currentTimeMillis()));
                return opt;
            });
    }

    // --- Synchronous Non-Blocking Accessors for Placeholders / GUI Cache ---

    /**
     * Returns the cached top 1 monthly record without blocking the tick thread.
     * If the cache is absent or expired, triggers an asynchronous reload in background.
     *
     * @param trackId track identifier
     * @return Optional containing record if present in cache
     */
    public Optional<RecordEntry> getCachedTop1Monthly(String trackId) {
        if (trackId == null) {
            return Optional.empty();
        }
        String cacheKey = trackId + ":" + getCurrentPeriodMonth();
        CachedRecords cached = monthlyCache.get(cacheKey);
        if (cached == null || cached.isExpired(cacheTtlMillis)) {
            refreshMonthlyAsync(trackId, getCurrentPeriodMonth());
        }
        if (cached != null && !cached.records().isEmpty()) {
            return Optional.of(cached.records().get(0));
        }
        return Optional.empty();
    }

    /**
     * Returns the cached top 1 all-time record without blocking the tick thread.
     * If the cache is absent or expired, triggers an asynchronous reload in background.
     *
     * @param trackId track identifier
     * @return Optional containing record if present in cache
     */
    public Optional<RecordEntry> getCachedTop1AllTime(String trackId) {
        if (trackId == null) {
            return Optional.empty();
        }
        CachedRecords cached = allTimeCache.get(trackId);
        if (cached == null || cached.isExpired(cacheTtlMillis)) {
            refreshAllTimeAsync(trackId);
        }
        if (cached != null && !cached.records().isEmpty()) {
            return Optional.of(cached.records().get(0));
        }
        return Optional.empty();
    }

    /**
     * Returns the cached personal best (all-time) record without blocking the tick thread.
     * Triggers an asynchronous reload in background if absent.
     *
     * @param trackId track identifier
     * @param playerUuid player UUID
     * @return Optional containing record if present in cache
     */
    public Optional<RecordEntry> getCachedPersonalBest(String trackId, UUID playerUuid) {
        if (trackId == null || playerUuid == null) {
            return Optional.empty();
        }
        String cacheKey = trackId + ":" + playerUuid + ":ALL";
        CachedPb cached = pbCache.get(cacheKey);
        if (cached == null || cached.isExpired(cacheTtlMillis)) {
            getPersonalBest(trackId, playerUuid);
        }
        return cached != null ? cached.record() : Optional.empty();
    }

    /**
     * Returns the cached monthly personal best record without blocking the tick thread.
     *
     * @param trackId track identifier
     * @param playerUuid player UUID
     * @return Optional containing record if present in cache
     */
    public Optional<RecordEntry> getCachedMonthlyPersonalBest(String trackId, UUID playerUuid) {
        if (trackId == null || playerUuid == null) {
            return Optional.empty();
        }
        String cacheKey = trackId + ":" + playerUuid + ":" + getCurrentPeriodMonth();
        CachedPb cached = pbCache.get(cacheKey);
        if (cached == null || cached.isExpired(cacheTtlMillis)) {
            getMonthlyPersonalBest(trackId, playerUuid);
        }
        return cached != null ? cached.record() : Optional.empty();
    }

    /**
     * Asynchronously saves a new race record, updates PB cache, and refreshes leaderboard caches.
     *
     * @param trackId track identifier
     * @param playerUuid player UUID
     * @param timeMillis time in milliseconds
     * @param laps laps completed
     * @return CompletableFuture completing when saved and caches updated
     */
    public CompletableFuture<Void> recordTime(String trackId, UUID playerUuid, long timeMillis, int laps) {
        String periodMonth = getCurrentPeriodMonth();
        RecordEntry entry = new RecordEntry(trackId, playerUuid, timeMillis, laps, periodMonth, java.time.Instant.now());

        return database.insertRecord(entry)
            .thenCompose(v -> {
                updatePbCacheIfFaster(trackId, playerUuid, entry);
                return refreshCache(trackId);
            });
    }

    /**
     * Asynchronously refreshes all cached top leaderboards for a track.
     *
     * @param trackId track identifier
     * @return CompletableFuture completing when caches are updated
     */
    public CompletableFuture<Void> refreshCache(String trackId) {
        Objects.requireNonNull(trackId, "trackId cannot be null");
        String period = getCurrentPeriodMonth();
        CompletableFuture<List<RecordEntry>> monthlyFuture = database.getTopMonthly(trackId, period, 50);
        CompletableFuture<List<RecordEntry>> allTimeFuture = database.getTopAllTime(trackId, 50);

        return CompletableFuture.allOf(monthlyFuture, allTimeFuture)
            .thenAccept(v -> {
                monthlyCache.put(trackId + ":" + period, new CachedRecords(monthlyFuture.join(), System.currentTimeMillis()));
                allTimeCache.put(trackId, new CachedRecords(allTimeFuture.join(), System.currentTimeMillis()));
            });
    }

    /**
     * Resets a ranking in the database and invalidates the cache.
     *
     * @param trackId track identifier
     * @param periodMonth month key or null/"ALL"
     * @return CompletableFuture with number of deleted rows
     */
    public CompletableFuture<Integer> resetRanking(String trackId, String periodMonth) {
        Objects.requireNonNull(trackId, "trackId cannot be null");
        return database.resetRanking(trackId, periodMonth)
            .thenApply(deleted -> {
                invalidateCache(trackId);
                return deleted;
            });
    }

    /**
     * Invalidates all memory caches for a track.
     *
     * @param trackId track identifier
     */
    public void invalidateCache(String trackId) {
        if (trackId == null) {
            return;
        }
        allTimeCache.remove(trackId);
        monthlyCache.keySet().removeIf(k -> k.startsWith(trackId + ":"));
        pbCache.keySet().removeIf(k -> k.startsWith(trackId + ":"));
    }

    /**
     * Clears all memory caches.
     */
    public void invalidateAll() {
        monthlyCache.clear();
        allTimeCache.clear();
        pbCache.clear();
    }

    /**
     * Cleans up all collections on shutdown.
     */
    public void cleanUp() {
        invalidateAll();
        playerNameCache.clear();
        if (instance == this) {
            instance = null;
        }
    }

    public BoatRaceDatabase getDatabase() {
        return database;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    // --- Internal Helpers ---

    private void refreshMonthlyAsync(String trackId, String periodMonth) {
        database.getTopMonthly(trackId, periodMonth, 50).thenAccept(records -> {
            monthlyCache.put(trackId + ":" + periodMonth, new CachedRecords(records, System.currentTimeMillis()));
        });
    }

    private void refreshAllTimeAsync(String trackId) {
        database.getTopAllTime(trackId, 50).thenAccept(records -> {
            allTimeCache.put(trackId, new CachedRecords(records, System.currentTimeMillis()));
        });
    }

    private void updatePbCacheIfFaster(String trackId, UUID playerUuid, RecordEntry newEntry) {
        String allKey = trackId + ":" + playerUuid + ":ALL";
        CachedPb cachedAll = pbCache.get(allKey);
        if (cachedAll == null || cachedAll.record().isEmpty() || newEntry.timeMillis() < cachedAll.record().get().timeMillis()) {
            pbCache.put(allKey, new CachedPb(Optional.of(newEntry), System.currentTimeMillis()));
        }

        String monthKey = trackId + ":" + playerUuid + ":" + newEntry.periodMonth();
        CachedPb cachedMonth = pbCache.get(monthKey);
        if (cachedMonth == null || cachedMonth.record().isEmpty() || newEntry.timeMillis() < cachedMonth.record().get().timeMillis()) {
            pbCache.put(monthKey, new CachedPb(Optional.of(newEntry), System.currentTimeMillis()));
        }
    }

    private List<RecordEntry> slice(List<RecordEntry> source, int limit) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source.subList(0, Math.min(limit, source.size()))));
    }

    private record CachedRecords(List<RecordEntry> records, long timestamp) {
        boolean isExpired(long ttl) {
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }

    private record CachedPb(Optional<RecordEntry> record, long timestamp) {
        boolean isExpired(long ttl) {
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }
}
