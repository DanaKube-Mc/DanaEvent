package fr.danakube.danaevent.core.team.manager;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.team.database.TeamDatabase;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamScoreEntry;
import fr.danakube.danaevent.core.team.strategy.BestMemberStrategy;
import fr.danakube.danaevent.core.team.strategy.MemberAverageStrategy;
import fr.danakube.danaevent.core.team.strategy.MemberSumStrategy;
import fr.danakube.danaevent.core.team.strategy.PlacementPointsStrategy;
import fr.danakube.danaevent.core.team.strategy.TeamScoringStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages team scoring strategies, tournament points calculation, and score persistence.
 */
public class TeamScoreManager {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM")
        .withZone(ZoneId.systemDefault());

    private final DanaEventPlugin plugin;
    private final TeamDatabase database;
    private final Map<String, TeamScoringStrategy> strategies = new ConcurrentHashMap<>();

    public TeamScoreManager(@NotNull DanaEventPlugin plugin, @NotNull TeamDatabase database) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.database = Objects.requireNonNull(database, "database cannot be null");

        // Register default strategies
        registerStrategy(new PlacementPointsStrategy());
        registerStrategy(new MemberSumStrategy());
        registerStrategy(new MemberAverageStrategy());
        registerStrategy(new BestMemberStrategy());
    }

    public @NotNull TeamDatabase getDatabase() {
        return database;
    }

    /**
     * Registers a new scoring strategy.
     *
     * @param strategy the strategy to register
     */
    public void registerStrategy(@NotNull TeamScoringStrategy strategy) {
        Objects.requireNonNull(strategy, "strategy cannot be null");
        strategies.put(strategy.getId().toLowerCase(), strategy);
    }

    /**
     * Retrieves a scoring strategy by its ID.
     *
     * @param id strategy identifier
     * @return Optional containing the strategy, or empty
     */
    public @NotNull Optional<TeamScoringStrategy> getStrategy(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(strategies.get(id.trim().toLowerCase()));
    }

    /**
     * Returns an unmodifiable collection of all registered scoring strategies.
     *
     * @return registered strategies
     */
    public @NotNull Collection<TeamScoringStrategy> getStrategies() {
        return Collections.unmodifiableCollection(strategies.values());
    }

    /**
     * Returns the current monthly period in 'yyyy-MM' format.
     *
     * @return current period string (e.g. '2026-09')
     */
    public @NotNull String getCurrentPeriodMonth() {
        return PERIOD_FORMATTER.format(Instant.now());
    }

    /**
     * Records a score value for a team in a specified period.
     *
     * @param teamId      team identifier
     * @param eventType   event type identifier
     * @param scoreValue  numerical score or points
     * @param periodMonth period key (e.g. '2026-09')
     * @return CompletableFuture completing when saved
     */
    public CompletableFuture<Void> recordScore(
        @NotNull String teamId,
        @NotNull String eventType,
        double scoreValue,
        @NotNull String periodMonth
    ) {
        return database.recordScore(teamId, eventType, scoreValue, periodMonth);
    }

    /**
     * Records a score value for a team in the current period.
     *
     * @param teamId     team identifier
     * @param eventType  event type identifier
     * @param scoreValue numerical score or points
     * @return CompletableFuture completing when saved
     */
    public CompletableFuture<Void> recordScore(
        @NotNull String teamId,
        @NotNull String eventType,
        double scoreValue
    ) {
        return recordScore(teamId, eventType, scoreValue, getCurrentPeriodMonth());
    }

    /**
     * Calculates the aggregate team score using a designated strategy and persists it.
     *
     * @param team         the DanaTeam
     * @param eventType    the event type identifier
     * @param strategyId   the scoring strategy ID
     * @param memberScores list of member scores
     * @param periodMonth  period month
     * @return CompletableFuture containing the calculated score
     */
    public CompletableFuture<Double> computeAndRecordTeamScore(
        @NotNull DanaTeam team,
        @NotNull String eventType,
        @NotNull String strategyId,
        @NotNull List<Double> memberScores,
        @NotNull String periodMonth
    ) {
        Objects.requireNonNull(team, "team cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(strategyId, "strategyId cannot be null");
        Objects.requireNonNull(memberScores, "memberScores cannot be null");
        Objects.requireNonNull(periodMonth, "periodMonth cannot be null");

        TeamScoringStrategy strategy = getStrategy(strategyId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown scoring strategy: " + strategyId));

        double calculatedScore = strategy.calculateScore(team, memberScores);
        return database.recordScore(team.getId(), eventType, calculatedScore, periodMonth)
            .thenApply(v -> calculatedScore);
    }

    /**
     * Retrieves the total accumulated score for a team in an event and period.
     *
     * @param teamId      team identifier
     * @param eventType   event type identifier
     * @param periodMonth period month key
     * @return CompletableFuture containing total score
     */
    public CompletableFuture<Double> getTeamTotalScore(
        @NotNull String teamId,
        @NotNull String eventType,
        @NotNull String periodMonth
    ) {
        return database.getTeamTotalScore(teamId, eventType, periodMonth);
    }

    /**
     * Retrieves the top team scores for an event and period.
     *
     * @param eventType   event type identifier
     * @param periodMonth period month key
     * @param limit       max records
     * @return CompletableFuture containing list of TeamScoreEntry
     */
    public CompletableFuture<List<TeamScoreEntry>> getLeaderboard(
        @NotNull String eventType,
        @NotNull String periodMonth,
        int limit
    ) {
        return database.getTopScores(eventType, periodMonth, limit);
    }

    /**
     * Resets scores for a given event and period.
     *
     * @param eventType   event type identifier
     * @param periodMonth period month key
     * @return CompletableFuture containing number of deleted records
     */
    public CompletableFuture<Integer> resetScores(
        @NotNull String eventType,
        @NotNull String periodMonth
    ) {
        return database.resetTeamScores(eventType, periodMonth);
    }
}
