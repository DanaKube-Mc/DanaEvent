package fr.danakube.danaevent.modules.treasurehunt.manager;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.modules.treasurehunt.database.TreasureHuntDatabase;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntRecord;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 * of treasure hunt leaderboards and personal records.
 */
public class HuntLeaderboardManager {

    public static final long DEFAULT_CACHE_TTL_MILLIS = 30_000L;

    private static HuntLeaderboardManager instance;

    private final DanaEventPlugin plugin;
    private final TreasureHuntDatabase database;
    private final long cacheTtlMillis;

    private final Map<String, CachedRecords> monthlyCache = new ConcurrentHashMap<>();
    private final Map<String, CachedRecords> allTimeCache = new ConcurrentHashMap<>();
    private final Map<String, CachedPb> pbCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> holderNameCache = new ConcurrentHashMap<>();

    public HuntLeaderboardManager(@NotNull TreasureHuntDatabase database) {
        this(null, database, DEFAULT_CACHE_TTL_MILLIS);
    }

    public HuntLeaderboardManager(@Nullable DanaEventPlugin plugin, @NotNull TreasureHuntDatabase database) {
        this(plugin, database, DEFAULT_CACHE_TTL_MILLIS);
    }

    public HuntLeaderboardManager(@Nullable DanaEventPlugin plugin, @NotNull TreasureHuntDatabase database, long cacheTtlMillis) {
        this.plugin = plugin;
        this.database = Objects.requireNonNull(database, "database cannot be null");
        this.cacheTtlMillis = Math.max(1000L, cacheTtlMillis);
    }

    public static HuntLeaderboardManager getInstance() {
        return instance;
    }

    public static void setInstance(HuntLeaderboardManager inst) {
        instance = inst;
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
                if (HuntProgressManager.getTeamUuid(team.getId()).equals(holderUuid)) {
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
            if (offline != null && offline.getName() != null) {
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

    public void registerHolderName(@NotNull UUID holderUuid, @NotNull String name) {
        holderNameCache.put(holderUuid, name);
    }

    public CompletableFuture<List<HuntRecord>> getTopMonthly(@NotNull String huntId, int limit) {
        return getTopMonthly(huntId, getCurrentPeriodMonth(), limit);
    }

    public CompletableFuture<List<HuntRecord>> getTopMonthly(@NotNull String huntId, @NotNull String periodMonth, int limit) {
        Objects.requireNonNull(huntId, "huntId cannot be null");
        Objects.requireNonNull(periodMonth, "periodMonth cannot be null");
        String cleanId = huntId.trim().toLowerCase();
        int effectiveLimit = Math.max(1, limit);
        String cacheKey = cleanId + ":" + periodMonth;

        CachedRecords cached = monthlyCache.get(cacheKey);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return CompletableFuture.completedFuture(slice(cached.records(), effectiveLimit));
        }

        return database.getTopMonthly(cleanId, periodMonth, Math.max(effectiveLimit, 50))
            .thenApply(records -> {
                monthlyCache.put(cacheKey, new CachedRecords(records, System.currentTimeMillis()));
                return slice(records, effectiveLimit);
            });
    }

    public CompletableFuture<List<HuntRecord>> getTopAllTime(@NotNull String huntId, int limit) {
        Objects.requireNonNull(huntId, "huntId cannot be null");
        String cleanId = huntId.trim().toLowerCase();
        int effectiveLimit = Math.max(1, limit);

        CachedRecords cached = allTimeCache.get(cleanId);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return CompletableFuture.completedFuture(slice(cached.records(), effectiveLimit));
        }

        return database.getTopAllTime(cleanId, Math.max(effectiveLimit, 50))
            .thenApply(records -> {
                allTimeCache.put(cleanId, new CachedRecords(records, System.currentTimeMillis()));
                return slice(records, effectiveLimit);
            });
    }

    public CompletableFuture<Optional<HuntRecord>> getPersonalBest(@NotNull String huntId, @NotNull UUID holderUuid) {
        Objects.requireNonNull(huntId, "huntId cannot be null");
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");
        String cleanId = huntId.trim().toLowerCase();
        String cacheKey = cleanId + ":" + holderUuid + ":ALL";

        CachedPb cached = pbCache.get(cacheKey);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return CompletableFuture.completedFuture(cached.record());
        }

        return database.getPersonalBest(cleanId, holderUuid)
            .thenApply(opt -> {
                pbCache.put(cacheKey, new CachedPb(opt, System.currentTimeMillis()));
                return opt;
            });
    }

    public Optional<HuntRecord> getCachedTop1AllTime(@Nullable String huntId) {
        if (huntId == null) {
            return Optional.empty();
        }
        String cleanId = huntId.trim().toLowerCase();
        CachedRecords cached = allTimeCache.get(cleanId);
        if (cached == null || cached.isExpired(cacheTtlMillis)) {
            getTopAllTime(cleanId, 50);
            cached = allTimeCache.get(cleanId);
        }
        if (cached != null && !cached.records().isEmpty()) {
            return Optional.of(cached.records().get(0));
        }
        return Optional.empty();
    }

    public Optional<HuntRecord> getCachedTop1Monthly(@Nullable String huntId) {
        if (huntId == null) {
            return Optional.empty();
        }
        String cleanId = huntId.trim().toLowerCase();
        String cacheKey = cleanId + ":" + getCurrentPeriodMonth();
        CachedRecords cached = monthlyCache.get(cacheKey);
        if (cached == null || cached.isExpired(cacheTtlMillis)) {
            getTopMonthly(cleanId, 50);
            cached = monthlyCache.get(cacheKey);
        }
        if (cached != null && !cached.records().isEmpty()) {
            return Optional.of(cached.records().get(0));
        }
        return Optional.empty();
    }

    public CompletableFuture<Integer> resetRanking(@NotNull String huntId, @Nullable String periodMonth) {
        Objects.requireNonNull(huntId, "huntId cannot be null");
        String cleanId = huntId.trim().toLowerCase();
        return database.resetRanking(cleanId, periodMonth)
            .thenApply(deleted -> {
                invalidateCache(cleanId);
                return deleted;
            });
    }

    public void invalidateCache(@Nullable String huntId) {
        if (huntId == null) {
            return;
        }
        String cleanId = huntId.trim().toLowerCase();
        allTimeCache.remove(cleanId);
        monthlyCache.keySet().removeIf(k -> k.startsWith(cleanId + ":"));
        pbCache.keySet().removeIf(k -> k.startsWith(cleanId + ":"));
    }

    public void invalidateAll() {
        monthlyCache.clear();
        allTimeCache.clear();
        pbCache.clear();
    }

    public void cleanUp() {
        invalidateAll();
        holderNameCache.clear();
        if (instance == this) {
            instance = null;
        }
    }

    private List<HuntRecord> slice(List<HuntRecord> source, int limit) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source.subList(0, Math.min(limit, source.size()))));
    }

    private record CachedRecords(List<HuntRecord> records, long timestamp) {
        boolean isExpired(long ttl) {
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }

    private record CachedPb(Optional<HuntRecord> record, long timestamp) {
        boolean isExpired(long ttl) {
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }
}
