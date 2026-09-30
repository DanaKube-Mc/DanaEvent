package fr.danakube.danaevent.modules.chromaticsheep.manager;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.modules.chromaticsheep.database.ChromaticSheepDatabase;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepRecord;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
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
 * Manages caching, name resolution (solo player or team), and asynchronous retrieval
 * of ChromaticSheep leaderboards and personal best records.
 */
public class SheepLeaderboardManager {

    public static final long DEFAULT_CACHE_TTL_MILLIS = 30_000L;

    private static SheepLeaderboardManager instance;

    private final DanaEventPlugin plugin;
    private final ChromaticSheepDatabase database;
    private final long cacheTtlMillis;

    private final Map<String, CachedRecords> monthlyCache = new ConcurrentHashMap<>();
    private final Map<String, CachedRecords> allTimeCache = new ConcurrentHashMap<>();
    private final Map<String, CachedPb> pbCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> holderNameCache = new ConcurrentHashMap<>();

    public SheepLeaderboardManager(@NotNull ChromaticSheepDatabase database) {
        this(null, database, DEFAULT_CACHE_TTL_MILLIS);
    }

    public SheepLeaderboardManager(@Nullable DanaEventPlugin plugin, @NotNull ChromaticSheepDatabase database) {
        this(plugin, database, DEFAULT_CACHE_TTL_MILLIS);
    }

    public SheepLeaderboardManager(@Nullable DanaEventPlugin plugin, @NotNull ChromaticSheepDatabase database, long cacheTtlMillis) {
        this.plugin = plugin;
        this.database = Objects.requireNonNull(database, "database cannot be null");
        this.cacheTtlMillis = Math.max(1000L, cacheTtlMillis);
    }

    public static SheepLeaderboardManager getInstance() {
        return instance;
    }

    public static void setInstance(SheepLeaderboardManager inst) {
        instance = inst;
    }

    public static UUID getTeamUuid(String teamId) {
        return UUID.nameUUIDFromBytes(("team:" + teamId).getBytes(StandardCharsets.UTF_8));
    }

    public String getCurrentPeriodMonth() {
        return YearMonth.now().toString();
    }

    /**
     * Resolves a holder's display name (player username or team formatted name).
     *
     * @param holderUuid UUID of the player or team
     * @param isTeam whether holder is a team
     * @return resolved display name
     */
    public String resolveHolderName(@NotNull UUID holderUuid, boolean isTeam) {
        String cached = holderNameCache.get(holderUuid);
        if (cached != null) {
            return cached;
        }

        if (isTeam && plugin != null && plugin.getTeamManager() != null) {
            for (DanaTeam team : plugin.getTeamManager().getTeams()) {
                if (getTeamUuid(team.getId()).equals(holderUuid)) {
                    String teamName = team.getDisplayName();
                    holderNameCache.put(holderUuid, teamName);
                    return teamName;
                }
            }
        }

        try {
            Player online = Bukkit.getPlayer(holderUuid);
            if (online != null && online.getName() != null) {
                holderNameCache.put(holderUuid, online.getName());
                return online.getName();
            }

            OfflinePlayer offline = Bukkit.getOfflinePlayer(holderUuid);
            if (offline != null && offline.getName() != null && (offline.hasPlayedBefore() || offline.isOnline())) {
                holderNameCache.put(holderUuid, offline.getName());
                return offline.getName();
            }
        } catch (Throwable ignored) {
        }

        if (database.getDatabaseManager() != null && database.getDatabaseManager().getStorageProvider() != null) {
            try {
                Optional<String> dbName = database.getDatabaseManager().getStorageProvider().loadPlayer(holderUuid);
                if (dbName.isPresent() && !dbName.get().isBlank()) {
                    holderNameCache.put(holderUuid, dbName.get());
                    return dbName.get();
                }
            } catch (Exception ignored) {
            }
        }

        return isTeam ? "Équipe Inconnue" : "Inconnu";
    }

    /**
     * Asynchronously retrieves top monthly records for an arena.
     */
    public CompletableFuture<List<SheepRecord>> getTopMonthly(@NotNull String arenaId, int limit) {
        String currentMonth = getCurrentPeriodMonth();
        return getTopMonthly(arenaId, currentMonth, limit);
    }

    /**
     * Asynchronously retrieves top monthly records for an arena and specific period month.
     */
    public CompletableFuture<List<SheepRecord>> getTopMonthly(@NotNull String arenaId, @NotNull String periodMonth, int limit) {
        String key = arenaId.toLowerCase() + ":" + periodMonth;
        CachedRecords cached = monthlyCache.get(key);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return CompletableFuture.completedFuture(limitList(cached.records(), limit));
        }

        return database.getTopMonthly(arenaId, periodMonth, Math.max(limit, 10))
            .thenApply(records -> {
                monthlyCache.put(key, new CachedRecords(records, System.currentTimeMillis()));
                return limitList(records, limit);
            });
    }

    /**
     * Asynchronously retrieves top all-time records for an arena.
     */
    public CompletableFuture<List<SheepRecord>> getTopAllTime(@NotNull String arenaId, int limit) {
        String key = arenaId.toLowerCase();
        CachedRecords cached = allTimeCache.get(key);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return CompletableFuture.completedFuture(limitList(cached.records(), limit));
        }

        return database.getTopAllTime(arenaId, Math.max(limit, 10))
            .thenApply(records -> {
                allTimeCache.put(key, new CachedRecords(records, System.currentTimeMillis()));
                return limitList(records, limit);
            });
    }

    /**
     * Asynchronously retrieves the personal best record for an arena.
     */
    public CompletableFuture<Optional<SheepRecord>> getPersonalBest(@NotNull String arenaId, @NotNull UUID holderUuid) {
        String key = arenaId.toLowerCase() + ":" + holderUuid;
        CachedPb cached = pbCache.get(key);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return CompletableFuture.completedFuture(cached.record());
        }

        return database.getPersonalBest(arenaId, holderUuid)
            .thenApply(recordOpt -> {
                pbCache.put(key, new CachedPb(recordOpt, System.currentTimeMillis()));
                return recordOpt;
            });
    }

    /**
     * Resets ranking for an arena and invalidates local caches.
     */
    public CompletableFuture<Integer> resetRanking(@NotNull String arenaId, @Nullable String periodMonth) {
        return database.resetRanking(arenaId, periodMonth)
            .thenApply(count -> {
                invalidate(arenaId);
                return count;
            });
    }

    public Optional<SheepRecord> getCachedTop1Monthly(@NotNull String arenaId) {
        String key = arenaId.toLowerCase() + ":" + getCurrentPeriodMonth();
        CachedRecords cached = monthlyCache.get(key);
        if (cached != null && !cached.records().isEmpty()) {
            return Optional.of(cached.records().get(0));
        }
        getTopMonthly(arenaId, 1);
        return Optional.empty();
    }

    public Optional<SheepRecord> getCachedTop1AllTime(@NotNull String arenaId) {
        String key = arenaId.toLowerCase();
        CachedRecords cached = allTimeCache.get(key);
        if (cached != null && !cached.records().isEmpty()) {
            return Optional.of(cached.records().get(0));
        }
        getTopAllTime(arenaId, 1);
        return Optional.empty();
    }

    public Optional<SheepRecord> getCachedPersonalBest(@NotNull String arenaId, @NotNull UUID holderUuid) {
        String key = arenaId.toLowerCase() + ":" + holderUuid;
        CachedPb cached = pbCache.get(key);
        if (cached != null) {
            return cached.record();
        }
        getPersonalBest(arenaId, holderUuid);
        return Optional.empty();
    }

    public void invalidate(@NotNull String arenaId) {
        String prefix = arenaId.toLowerCase();
        monthlyCache.keySet().removeIf(k -> k.startsWith(prefix + ":") || k.equals(prefix));
        allTimeCache.remove(prefix);
        pbCache.keySet().removeIf(k -> k.startsWith(prefix + ":"));
    }

    public void invalidateAll() {
        monthlyCache.clear();
        allTimeCache.clear();
        pbCache.clear();
        holderNameCache.clear();
    }

    public void refreshCache() {
        invalidateAll();
    }

    public void cleanUp() {
        invalidateAll();
    }

    private <T> List<T> limitList(List<T> list, int limit) {
        if (list.size() <= limit) {
            return list;
        }
        return Collections.unmodifiableList(new ArrayList<>(list.subList(0, limit)));
    }

    private record CachedRecords(List<SheepRecord> records, long timestamp) {
        boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - timestamp > ttlMillis;
        }
    }

    private record CachedPb(Optional<SheepRecord> record, long timestamp) {
        boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - timestamp > ttlMillis;
        }
    }
}
