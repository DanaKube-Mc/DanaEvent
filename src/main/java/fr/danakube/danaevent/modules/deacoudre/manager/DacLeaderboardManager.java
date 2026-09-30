package fr.danakube.danaevent.modules.deacoudre.manager;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.modules.deacoudre.database.DeACoudreDatabase;
import fr.danakube.danaevent.modules.deacoudre.model.DacRecord;
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
 * Manages caching, name resolution, and asynchronous access to Dé à Coudre records and leaderboards.
 */
public class DacLeaderboardManager {

    public static final long DEFAULT_CACHE_TTL_MILLIS = 30_000L;

    private static DacLeaderboardManager instance;

    private final DanaEventPlugin plugin;
    private final DeACoudreDatabase database;
    private final long cacheTtlMillis;

    private final Map<String, CachedRecords> monthlyCache = new ConcurrentHashMap<>();
    private final Map<String, CachedRecords> allTimeCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> holderNameCache = new ConcurrentHashMap<>();

    public DacLeaderboardManager(@NotNull DeACoudreDatabase database) {
        this(null, database, DEFAULT_CACHE_TTL_MILLIS);
    }

    public DacLeaderboardManager(@Nullable DanaEventPlugin plugin, @NotNull DeACoudreDatabase database) {
        this(plugin, database, DEFAULT_CACHE_TTL_MILLIS);
    }

    public DacLeaderboardManager(@Nullable DanaEventPlugin plugin, @NotNull DeACoudreDatabase database, long cacheTtlMillis) {
        this.plugin = plugin;
        this.database = Objects.requireNonNull(database, "database cannot be null");
        this.cacheTtlMillis = Math.max(1000L, cacheTtlMillis);
    }

    public static DacLeaderboardManager getInstance() {
        return instance;
    }

    public static void setInstance(DacLeaderboardManager inst) {
        instance = inst;
    }

    public String getCurrentPeriodMonth() {
        return YearMonth.now().toString();
    }

    public static UUID getTeamUuid(String teamId) {
        return UUID.nameUUIDFromBytes(("team:" + teamId).getBytes(StandardCharsets.UTF_8));
    }

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
            String fallbackTeam = "Équipe " + holderUuid.toString().substring(0, 8);
            holderNameCache.put(holderUuid, fallbackTeam);
            return fallbackTeam;
        }

        Player onlinePlayer = Bukkit.getPlayer(holderUuid);
        if (onlinePlayer != null) {
            holderNameCache.put(holderUuid, onlinePlayer.getName());
            return onlinePlayer.getName();
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(holderUuid);
        if (offlinePlayer.getName() != null) {
            holderNameCache.put(holderUuid, offlinePlayer.getName());
            return offlinePlayer.getName();
        }

        String fallback = holderUuid.toString().substring(0, 8);
        holderNameCache.put(holderUuid, fallback);
        return fallback;
    }

    public CompletableFuture<List<DacRecord>> getTopMonthly(@NotNull String arenaId, int limit) {
        String period = getCurrentPeriodMonth();
        String cacheKey = arenaId.toLowerCase() + ":" + period + ":" + limit;
        CachedRecords cached = monthlyCache.get(cacheKey);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return CompletableFuture.completedFuture(cached.records());
        }

        return database.getTopMonthly(arenaId, period, limit).thenApply(records -> {
            monthlyCache.put(cacheKey, new CachedRecords(records, System.currentTimeMillis()));
            return records;
        });
    }

    public CompletableFuture<List<DacRecord>> getTopAllTime(@NotNull String arenaId, int limit) {
        String cacheKey = arenaId.toLowerCase() + ":" + limit;
        CachedRecords cached = allTimeCache.get(cacheKey);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return CompletableFuture.completedFuture(cached.records());
        }

        return database.getTopAllTime(arenaId, limit).thenApply(records -> {
            allTimeCache.put(cacheKey, new CachedRecords(records, System.currentTimeMillis()));
            return records;
        });
    }

    public CompletableFuture<Optional<DacRecord>> getPersonalStats(@NotNull String arenaId, @NotNull UUID holderUuid) {
        return database.getPersonalStats(arenaId, holderUuid);
    }

    public CompletableFuture<Integer> getPlayerTotalPerfects(@NotNull UUID playerUuid) {
        return database.getPlayerTotalPerfects(playerUuid);
    }

    public Optional<DacRecord> getCachedTop1Monthly(@NotNull String arenaId) {
        String period = getCurrentPeriodMonth();
        String cacheKey = arenaId.toLowerCase() + ":" + period + ":10";
        CachedRecords cached = monthlyCache.get(cacheKey);
        if (cached != null && !cached.records().isEmpty()) {
            return Optional.of(cached.records().get(0));
        }
        // Asynchronously populate cache in background
        getTopMonthly(arenaId, 10);
        return Optional.empty();
    }

    public Optional<DacRecord> getCachedTop1AllTime(@NotNull String arenaId) {
        String cacheKey = arenaId.toLowerCase() + ":10";
        CachedRecords cached = allTimeCache.get(cacheKey);
        if (cached != null && !cached.records().isEmpty()) {
            return Optional.of(cached.records().get(0));
        }
        getTopAllTime(arenaId, 10);
        return Optional.empty();
    }

    public int getCachedPlayerPerfects(@NotNull UUID playerUuid) {
        // Asynchronously populate in background
        getPlayerTotalPerfects(playerUuid);
        return 0;
    }

    public CompletableFuture<Integer> resetRanking(@NotNull String arenaId, @Nullable String periodMonth) {
        invalidateAll();
        return database.resetRanking(arenaId, periodMonth);
    }

    public void invalidateAll() {
        monthlyCache.clear();
        allTimeCache.clear();
        holderNameCache.clear();
    }

    private record CachedRecords(List<DacRecord> records, long timestamp) {
        boolean isExpired(long ttl) {
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }
}
