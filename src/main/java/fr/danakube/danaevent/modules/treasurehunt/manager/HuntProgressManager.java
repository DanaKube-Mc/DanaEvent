package fr.danakube.danaevent.modules.treasurehunt.manager;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.modules.treasurehunt.config.HuntConfig;
import fr.danakube.danaevent.modules.treasurehunt.database.TreasureHuntDatabase;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntRecord;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntStep;
import fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active player and team hunt sessions, step validation with anti-sequence checks,
 * crash recovery, and rewards.
 */
public class HuntProgressManager {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM")
        .withZone(ZoneId.systemDefault());

    private final DanaEventPlugin plugin;
    private final HuntConfig huntConfig;
    private final TreasureHuntDatabase database;
    private final HuntPathDistributor pathDistributor;
    private final Map<UUID, PlayerHuntProgress> activeProgress = new ConcurrentHashMap<>();

    public HuntProgressManager(
        @Nullable DanaEventPlugin plugin,
        @NotNull HuntConfig huntConfig,
        @NotNull TreasureHuntDatabase database
    ) {
        this(plugin, huntConfig, database, new HuntPathDistributor());
    }

    public HuntProgressManager(
        @Nullable DanaEventPlugin plugin,
        @NotNull HuntConfig huntConfig,
        @NotNull TreasureHuntDatabase database,
        @NotNull HuntPathDistributor pathDistributor
    ) {
        this.plugin = plugin;
        this.huntConfig = Objects.requireNonNull(huntConfig, "huntConfig cannot be null");
        this.database = Objects.requireNonNull(database, "database cannot be null");
        this.pathDistributor = Objects.requireNonNull(pathDistributor, "pathDistributor cannot be null");
    }

    public static UUID getTeamUuid(@NotNull String teamId) {
        return UUID.nameUUIDFromBytes(("team:" + teamId.trim().toLowerCase()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public @NotNull Collection<PlayerHuntProgress> getActiveSessions() {
        return Collections.unmodifiableCollection(activeProgress.values());
    }

    public Optional<PlayerHuntProgress> getActiveProgress(@NotNull UUID holderUuid) {
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");
        return Optional.ofNullable(activeProgress.get(holderUuid));
    }

    public Optional<PlayerHuntProgress> getProgressForPlayer(@NotNull Player player) {
        Objects.requireNonNull(player, "player cannot be null");
        PlayerHuntProgress solo = activeProgress.get(player.getUniqueId());
        if (solo != null) {
            return Optional.of(solo);
        }
        if (plugin != null && plugin.getTeamManager() != null) {
            Optional<fr.danakube.danaevent.core.team.model.DanaTeam> teamOpt = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
            if (teamOpt.isPresent()) {
                UUID teamUuid = getTeamUuid(teamOpt.get().getId());
                return Optional.ofNullable(activeProgress.get(teamUuid));
            }
        }
        return Optional.empty();
    }

    public boolean isParticipant(@NotNull UUID holderUuid) {
        return activeProgress.containsKey(Objects.requireNonNull(holderUuid, "holderUuid cannot be null"));
    }

    public boolean isParticipant(@NotNull Player player) {
        return getProgressForPlayer(player).isPresent();
    }

    /**
     * Starts a new treasure hunt session for a player or team.
     *
     * @param holderUuid player or team UUID
     * @param isTeam true if team session, false if solo
     * @param huntId identifier of the hunt
     * @return Optional containing the started PlayerHuntProgress, or empty if unable to start
     */
    public Optional<PlayerHuntProgress> startHunt(@NotNull UUID holderUuid, boolean isTeam, @NotNull String huntId) {
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");
        Objects.requireNonNull(huntId, "huntId cannot be null");

        if (activeProgress.containsKey(holderUuid)) {
            return Optional.empty();
        }

        Optional<Hunt> huntOpt = huntConfig.getHunt(huntId);
        if (huntOpt.isEmpty()) {
            return Optional.empty();
        }

        Hunt hunt = huntOpt.get();
        if (!hunt.isEnabled() || !hunt.isReady()) {
            return Optional.empty();
        }

        List<Integer> stepOrder = pathDistributor.generateStepOrder(hunt);
        PlayerHuntProgress progress = PlayerHuntProgress.start(holderUuid, isTeam, hunt.getId(), stepOrder);
        activeProgress.put(holderUuid, progress);

        // Save progress to database asynchronously for crash recovery
        database.saveProgress(progress);

        return Optional.of(progress);
    }

    /**
     * Validates a step triggered by a player or team.
     *
     * @param holderUuid holder UUID
     * @param triggeredStepNumber 1-based step number that was triggered
     * @return CompletableFuture containing the StepValidationResult
     */
    public CompletableFuture<StepValidationResult> validateStep(@NotNull UUID holderUuid, int triggeredStepNumber) {
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");

        PlayerHuntProgress progress = activeProgress.get(holderUuid);
        if (progress == null) {
            return CompletableFuture.completedFuture(StepValidationResult.NOT_IN_HUNT);
        }

        if (progress.isCompleted()) {
            return CompletableFuture.completedFuture(StepValidationResult.NOT_IN_HUNT);
        }

        int activeExpectedStep = progress.getActiveStepNumber();
        if (activeExpectedStep != triggeredStepNumber) {
            return CompletableFuture.completedFuture(StepValidationResult.WRONG_STEP);
        }

        Optional<Hunt> huntOpt = huntConfig.getHunt(progress.getHuntId());
        if (huntOpt.isEmpty() || !huntOpt.get().isEnabled()) {
            return CompletableFuture.completedFuture(StepValidationResult.HUNT_DISABLED);
        }

        Hunt hunt = huntOpt.get();
        HuntStep completedStep = hunt.getStep(triggeredStepNumber).orElse(null);

        // Dispatch intermediate step rewards
        if (completedStep != null) {
            dispatchStepRewards(holderUuid, progress.isTeam(), completedStep);
        }

        if (progress.isLastStep()) {
            // Hunt finished!
            long now = System.currentTimeMillis();
            progress.markCompleted(now);
            long totalTime = progress.getElapsedTimeMillis();
            String currentMonth = getCurrentPeriodMonth();

            HuntRecord record = new HuntRecord(
                progress.getHuntId(),
                holderUuid,
                progress.isTeam(),
                totalTime,
                currentMonth,
                Instant.now()
            );

            // Dispatch final rewards
            dispatchFinalRewards(holderUuid, progress.isTeam(), hunt);

            activeProgress.remove(holderUuid);

            return database.saveRecord(record)
                .thenCompose(v -> database.deleteProgress(holderUuid))
                .thenApply(v -> StepValidationResult.HUNT_COMPLETED);
        } else {
            // Advance to next step
            progress.advanceStep();
            return database.saveProgress(progress)
                .thenApply(v -> StepValidationResult.STEP_ADVANCED);
        }
    }

    /**
     * Validates a step triggered by a player, resolving solo or team progress and broadcasting feedback.
     *
     * @param player player who triggered the step
     * @param triggeredStepNumber 1-based step number triggered
     * @return CompletableFuture containing StepValidationResult
     */
    public CompletableFuture<StepValidationResult> validateStepForPlayer(@NotNull Player player, int triggeredStepNumber) {
        Objects.requireNonNull(player, "player cannot be null");
        Optional<PlayerHuntProgress> progressOpt = getProgressForPlayer(player);
        if (progressOpt.isEmpty()) {
            return CompletableFuture.completedFuture(StepValidationResult.NOT_IN_HUNT);
        }

        PlayerHuntProgress progress = progressOpt.get();
        UUID holderUuid = progress.getHolderUuid();
        boolean isTeam = progress.isTeam();
        int stepBefore = progress.getActiveStepNumber();

        return validateStep(holderUuid, triggeredStepNumber).thenApply(result -> {
            runSync(() -> {
                if (result == StepValidationResult.STEP_ADVANCED) {
                    if (isTeam && plugin != null && plugin.getTeamManager() != null) {
                        for (DanaTeam team : plugin.getTeamManager().getTeams()) {
                            if (getTeamUuid(team.getId()).equals(holderUuid)) {
                                for (UUID memberUuid : team.getMembers().keySet()) {
                                    Player member = Bukkit.getPlayer(memberUuid);
                                    if (member != null && member.isOnline()) {
                                        if (plugin.getMessageManager() != null) {
                                            plugin.getMessageManager().sendMessage(
                                                member,
                                                "hunt-team-step-broadcast",
                                                Placeholder.parsed("player", player.getName()),
                                                Placeholder.parsed("step", String.valueOf(stepBefore))
                                            );
                                        }
                                        member.playSound(member.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                                    }
                                }
                                break;
                            }
                        }
                    } else {
                        if (plugin != null && plugin.getMessageManager() != null) {
                            plugin.getMessageManager().sendMessage(player, "hunt-step-completed");
                        }
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    }
                } else if (result == StepValidationResult.HUNT_COMPLETED) {
                    String formattedTime = progress.formatElapsedTime();
                    if (isTeam && plugin != null && plugin.getTeamManager() != null) {
                        for (DanaTeam team : plugin.getTeamManager().getTeams()) {
                            if (getTeamUuid(team.getId()).equals(holderUuid)) {
                                for (UUID memberUuid : team.getMembers().keySet()) {
                                    Player member = Bukkit.getPlayer(memberUuid);
                                    if (member != null && member.isOnline()) {
                                        if (plugin.getMessageManager() != null) {
                                            plugin.getMessageManager().sendMessage(
                                                member,
                                                "hunt-completed",
                                                Placeholder.parsed("time", formattedTime)
                                            );
                                        }
                                        member.playSound(member.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                                    }
                                }
                                break;
                            }
                        }
                    } else {
                        if (plugin != null && plugin.getMessageManager() != null) {
                            plugin.getMessageManager().sendMessage(
                                player,
                                "hunt-completed",
                                Placeholder.parsed("time", formattedTime)
                            );
                        }
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    }
                }
            });
            return result;
        });
    }

    /**
     * Resumes an in-progress hunt for a player or team from database if orphaned.
     *
     * @param holderUuid player or team UUID
     * @return CompletableFuture with resumed PlayerHuntProgress if present
     */
    public CompletableFuture<Optional<PlayerHuntProgress>> loadOrResumeProgress(@NotNull UUID holderUuid) {
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");

        PlayerHuntProgress existing = activeProgress.get(holderUuid);
        if (existing != null) {
            return CompletableFuture.completedFuture(Optional.of(existing));
        }

        return database.loadProgress(holderUuid).thenApply(opt -> {
            opt.ifPresent(p -> activeProgress.put(holderUuid, p));
            return opt;
        });
    }

    /**
     * Cancels an ongoing hunt for a participant.
     *
     * @param holderUuid player or team UUID
     * @return CompletableFuture completing with true if cancelled
     */
    public CompletableFuture<Boolean> cancelHunt(@NotNull UUID holderUuid) {
        Objects.requireNonNull(holderUuid, "holderUuid cannot be null");

        PlayerHuntProgress removed = activeProgress.remove(holderUuid);
        return database.deleteProgress(holderUuid).thenApply(v -> removed != null);
    }

    public void cleanUp() {
        activeProgress.clear();
    }

    public String getCurrentPeriodMonth() {
        return PERIOD_FORMATTER.format(Instant.now());
    }

    private void dispatchStepRewards(UUID holderUuid, boolean isTeam, HuntStep step) {
        if (step.getRewardItem() == null && step.getRewardCommands().isEmpty()) {
            return;
        }

        if (isTeam && plugin != null && plugin.getTeamManager() != null) {
            for (DanaTeam team : plugin.getTeamManager().getTeams()) {
                if (getTeamUuid(team.getId()).equals(holderUuid)) {
                    for (UUID memberUuid : team.getMembers().keySet()) {
                        Player member = Bukkit.getPlayer(memberUuid);
                        if (member != null && member.isOnline()) {
                            giveReward(member, step.getRewardItem(), step.getRewardCommands());
                        }
                    }
                    return;
                }
            }
        }

        Player player = Bukkit.getPlayer(holderUuid);
        if (player != null && player.isOnline()) {
            giveReward(player, step.getRewardItem(), step.getRewardCommands());
        }
    }

    private void dispatchFinalRewards(UUID holderUuid, boolean isTeam, Hunt hunt) {
        if (hunt.getFinalRewardItem() == null && hunt.getFinalRewardCommands().isEmpty()) {
            return;
        }

        if (isTeam && plugin != null && plugin.getTeamManager() != null) {
            for (DanaTeam team : plugin.getTeamManager().getTeams()) {
                if (getTeamUuid(team.getId()).equals(holderUuid)) {
                    for (UUID memberUuid : team.getMembers().keySet()) {
                        Player member = Bukkit.getPlayer(memberUuid);
                        if (member != null && member.isOnline()) {
                            giveReward(member, hunt.getFinalRewardItem(), hunt.getFinalRewardCommands());
                        }
                    }
                    return;
                }
            }
        }

        Player player = Bukkit.getPlayer(holderUuid);
        if (player != null && player.isOnline()) {
            giveReward(player, hunt.getFinalRewardItem(), hunt.getFinalRewardCommands());
        }
    }

    private void giveReward(Player player, ItemStack item, List<String> commands) {
        if (item != null) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }

        for (String cmd : commands) {
            String formatted = cmd.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formatted);
        }
    }

    private void runSync(@NotNull Runnable action) {
        if (plugin != null && plugin.isEnabled() && !Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, action);
        } else {
            action.run();
        }
    }
}
