package fr.danakube.danaevent.modules.chromaticsheep.manager;

import fr.danakube.danaevent.core.player.PlayerStateManager;
import fr.danakube.danaevent.core.team.manager.TeamManager;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.modules.chromaticsheep.database.ChromaticSheepDatabase;
import fr.danakube.danaevent.modules.chromaticsheep.item.PaintBombItem;
import fr.danakube.danaevent.modules.chromaticsheep.item.PaintBrushItem;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameFormat;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameState;
import fr.danakube.danaevent.modules.chromaticsheep.model.PlayerSheepSession;
import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepArena;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepGame;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepRecord;
import fr.danakube.danaevent.modules.chromaticsheep.task.DominationTickTask;
import fr.danakube.danaevent.modules.chromaticsheep.task.GameTimerTask;
import fr.danakube.danaevent.modules.chromaticsheep.task.HudUpdateTask;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core game manager orchestrating ChromaticSheep matches, player lifecycles, and tasks.
 */
public class SheepGameManager {

    private static final List<DyeColor> AVAILABLE_COLORS = List.of(
        DyeColor.RED, DyeColor.BLUE, DyeColor.GREEN, DyeColor.YELLOW,
        DyeColor.PURPLE, DyeColor.ORANGE, DyeColor.CYAN, DyeColor.PINK,
        DyeColor.LIME, DyeColor.LIGHT_BLUE, DyeColor.MAGENTA, DyeColor.BROWN,
        DyeColor.GRAY, DyeColor.LIGHT_GRAY, DyeColor.BLACK, DyeColor.WHITE
    );

    private final Plugin plugin;
    private final PlayerStateManager playerStateManager;
    private final ArenaManager arenaManager;
    private final HerdManager herdManager;
    private final SheepScoreManager scoreManager;
    private final ChromaticSheepDatabase database;
    private final TeamManager teamManager;

    private final Map<String, SheepGame> activeGames = new ConcurrentHashMap<>();

    public SheepGameManager(
        @NotNull Plugin plugin,
        @NotNull PlayerStateManager playerStateManager,
        @NotNull ArenaManager arenaManager,
        @NotNull HerdManager herdManager,
        @NotNull SheepScoreManager scoreManager,
        @NotNull ChromaticSheepDatabase database,
        @Nullable TeamManager teamManager
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.playerStateManager = Objects.requireNonNull(playerStateManager, "playerStateManager cannot be null");
        this.arenaManager = Objects.requireNonNull(arenaManager, "arenaManager cannot be null");
        this.herdManager = Objects.requireNonNull(herdManager, "herdManager cannot be null");
        this.scoreManager = Objects.requireNonNull(scoreManager, "scoreManager cannot be null");
        this.database = Objects.requireNonNull(database, "database cannot be null");
        this.teamManager = teamManager;
    }

    public @Nullable SheepGame getGame(@NotNull String arenaId) {
        return activeGames.get(arenaId.trim().toLowerCase());
    }

    public Optional<PlayerSheepSession> getSession(@NotNull UUID playerUuid) {
        for (SheepGame game : activeGames.values()) {
            PlayerSheepSession session = game.getSessions().get(playerUuid);
            if (session != null) {
                return Optional.of(session);
            }
        }
        return Optional.empty();
    }

    public Optional<SheepArena> getPlayerArena(@NotNull UUID playerUuid) {
        for (SheepGame game : activeGames.values()) {
            if (game.getSessions().containsKey(playerUuid)) {
                return Optional.of(game.getArena());
            }
        }
        return Optional.empty();
    }

    /**
     * Enrolls a player into an arena match.
     */
    public boolean joinGame(@NotNull Player player, @NotNull String arenaId) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(arenaId, "arenaId cannot be null");

        // Player cannot be already in another event/snapshot
        if (playerStateManager.hasSnapshot(player.getUniqueId()) || getSession(player.getUniqueId()).isPresent()) {
            return false;
        }

        Optional<SheepArena> arenaOpt = arenaManager.getArena(arenaId);
        if (arenaOpt.isEmpty()) {
            return false;
        }

        SheepArena arena = arenaOpt.get();
        if (!arena.isEnabled() || !arena.isReady()) {
            return false;
        }

        SheepGame game = activeGames.computeIfAbsent(arena.getId(), k -> new SheepGame(arena));
        if (game.getState() != GameState.WAITING && game.getState() != GameState.COUNTDOWN) {
            return false;
        }

        DyeColor assignedColor = null;
        UUID teamUuid = null;

        if (arena.getFormat() == GameFormat.TEAM) {
            if (teamManager == null) {
                return false;
            }
            Optional<DanaTeam> teamOpt = teamManager.getPlayerTeam(player.getUniqueId());
            if (teamOpt.isEmpty()) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Vous devez être dans une équipe pour participer à cette arène !</red>"));
                return false;
            }
            DanaTeam team = teamOpt.get();
            teamUuid = UUID.nameUUIDFromBytes(("team:" + team.getId()).getBytes(StandardCharsets.UTF_8));
            try {
                assignedColor = DyeColor.valueOf(team.getColor().name());
            } catch (IllegalArgumentException e) {
                assignedColor = DyeColor.BLUE;
            }
        } else {
            // SOLO: pick first available color
            for (DyeColor color : AVAILABLE_COLORS) {
                if (!game.isColorUsed(color)) {
                    assignedColor = color;
                    break;
                }
            }
            if (assignedColor == null) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>L'arène est pleine (16 joueurs maximum) !</red>"));
                return false;
            }
        }

        // Save inventory and state
        playerStateManager.saveAndClear(player);

        // Teleport to spawn
        List<Location> spawns = arena.getPlayerSpawns();
        if (!spawns.isEmpty()) {
            int index = game.getSessions().size() % spawns.size();
            player.teleport(spawns.get(index));
        }

        // Equip tools
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().setItem(0, PaintBrushItem.createItem());
        player.getInventory().setItem(1, PaintBombItem.createItem());

        // Create session
        PlayerSheepSession session = new PlayerSheepSession(player.getUniqueId(), teamUuid, assignedColor, arena.getId());
        game.addSession(session);

        player.sendMessage(MiniMessage.miniMessage().deserialize(
            "<green>Vous avez rejoint la partie sur l'arène <yellow>" + arena.getDisplayName() + "</yellow> !</green>"
        ));

        return true;
    }

    /**
     * Removes a player from their active match and restores their inventory.
     */
    public boolean leaveGame(@NotNull Player player) {
        Objects.requireNonNull(player, "player cannot be null");

        Optional<PlayerSheepSession> sessionOpt = getSession(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return false;
        }

        PlayerSheepSession session = sessionOpt.get();
        SheepGame game = activeGames.get(session.getArenaId());
        if (game == null) {
            return false;
        }

        if (game.getBossBar() != null) {
            player.hideBossBar(game.getBossBar());
        }

        game.removeSession(player.getUniqueId());
        playerStateManager.restore(player, true);

        player.sendMessage(MiniMessage.miniMessage().deserialize(
            "<yellow>Vous avez quitté la partie de MoutonChromatique.</yellow>"
        ));

        // If game is running and no players remain, stop it
        if (game.getSessions().isEmpty()) {
            stopGame(session.getArenaId());
        }

        return true;
    }

    /**
     * Starts the game on the given arena.
     */
    public boolean startGame(@NotNull String arenaId) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        SheepGame game = activeGames.get(arenaId.trim().toLowerCase());
        if (game == null || game.getSessions().isEmpty()) {
            return false;
        }

        SheepArena arena = game.getArena();
        List<Location> spawns = arena.getPlayerSpawns();
        if (spawns.isEmpty() || arena.getBounds() == null) {
            return false;
        }

        World world = spawns.get(0).getWorld();
        if (world == null) {
            return false;
        }

        game.setState(GameState.RUNNING);
        game.setRemainingSeconds(arena.getDurationSeconds());
        scoreManager.resetArena(arena.getId());

        // Spawn herd
        herdManager.spawnHerd(arena, world);

        // Setup BossBar
        BossBar bossBar = BossBar.bossBar(
            MiniMessage.miniMessage().deserialize("<!italic><gradient:#ff5555:#ffff55><b>MoutonChromatique</b></gradient>"),
            1.0f,
            BossBar.Color.GREEN,
            BossBar.Overlay.PROGRESS
        );
        game.setBossBar(bossBar);

        for (UUID uuid : game.getSessions().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.showBossBar(bossBar);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                p.showTitle(Title.title(
                    MiniMessage.miniMessage().deserialize("<gradient:#ff5555:#ffff55><b>C'EST PARTI !</b></gradient>"),
                    MiniMessage.miniMessage().deserialize("<white>Teignez un maximum de moutons !</white>"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))
                ));
            }
        }

        // Schedule tasks
        BukkitTask timerTask = Bukkit.getScheduler().runTaskTimer(plugin, new GameTimerTask(this, arena.getId()), 20L, 20L);
        game.setTimerTask(timerTask);

        if (arena.getScoringMode() == ScoringMode.DOMINATION_TICK) {
            BukkitTask dominationTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                new DominationTickTask(this, herdManager, scoreManager, arena.getId()),
                40L,
                40L
            );
            game.setDominationTask(dominationTask);
        }

        BukkitTask hudTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            new HudUpdateTask(this, scoreManager, arena.getId()),
            10L,
            10L
        );
        game.setHudTask(hudTask);

        return true;
    }

    /**
     * Concludes the match, computes scores, persists records, and restores players.
     */
    public void endGame(@NotNull String arenaId) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        SheepGame game = activeGames.remove(arenaId.trim().toLowerCase());
        if (game == null) {
            return;
        }

        game.setState(GameState.ENDED);
        game.cancelAllTasks();

        SheepArena arena = game.getArena();
        World world = arena.getPlayerSpawns().isEmpty() ? null : arena.getPlayerSpawns().get(0).getWorld();

        // Count final colors if FINAL_COUNT
        Map<DyeColor, Integer> colorCounts = world != null ? herdManager.countColors(arena.getId(), world) : Collections.emptyMap();
        if (arena.getScoringMode() == ScoringMode.FINAL_COUNT) {
            for (PlayerSheepSession session : game.getSessions().values()) {
                int count = colorCounts.getOrDefault(session.getColor(), 0);
                scoreManager.setScore(arena.getId(), session.getEffectiveHolderUuid(), count);
            }
        }

        List<Map.Entry<UUID, Integer>> leaderboard = scoreManager.getLeaderboard(arena.getId());
        UUID winnerUuid = leaderboard.isEmpty() ? null : leaderboard.get(0).getKey();
        int winnerScore = leaderboard.isEmpty() ? 0 : leaderboard.get(0).getValue();

        String periodMonth = DateTimeFormatter.ofPattern("yyyy-MM").format(LocalDate.now());

        // Asynchronously persist records
        for (PlayerSheepSession session : game.getSessions().values()) {
            UUID holder = session.getEffectiveHolderUuid();
            int score = scoreManager.getScore(arena.getId(), holder);
            int sheepCount = colorCounts.getOrDefault(session.getColor(), 0);

            SheepRecord record = new SheepRecord(
                arena.getId(),
                holder,
                session.isTeam(),
                score,
                sheepCount,
                arena.getScoringMode(),
                periodMonth,
                java.time.Instant.now()
            );
            database.saveRecord(record);
        }

        // Restore players and show end title
        for (PlayerSheepSession session : game.getSessions().values()) {
            Player player = Bukkit.getPlayer(session.getPlayerUuid());
            if (player != null && player.isOnline()) {
                if (game.getBossBar() != null) {
                    player.hideBossBar(game.getBossBar());
                }

                if (session.getEffectiveHolderUuid().equals(winnerUuid)) {
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    player.showTitle(Title.title(
                        MiniMessage.miniMessage().deserialize("<gold><b>VICTOIRE !</b></gold>"),
                        MiniMessage.miniMessage().deserialize("<yellow>Score final : " + winnerScore + " pts</yellow>"),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))
                    ));
                } else {
                    player.showTitle(Title.title(
                        MiniMessage.miniMessage().deserialize("<red><b>PARTIE TERMINÉE</b></red>"),
                        MiniMessage.miniMessage().deserialize("<gray>Votre score : " + scoreManager.getScore(arena.getId(), session.getEffectiveHolderUuid()) + " pts</gray>"),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))
                    ));
                }

                playerStateManager.restore(player, true);
            }
        }

        // 100% Entity cleanup
        herdManager.cleanUpArena(arena.getId(), world);
        scoreManager.resetArena(arena.getId());
    }

    /**
     * Immediately stops a game (admin command or cancellation).
     */
    public void stopGame(@NotNull String arenaId) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        SheepGame game = activeGames.remove(arenaId.trim().toLowerCase());
        if (game == null) {
            return;
        }

        game.cancelAllTasks();
        SheepArena arena = game.getArena();
        World world = arena.getPlayerSpawns().isEmpty() ? null : arena.getPlayerSpawns().get(0).getWorld();

        for (PlayerSheepSession session : game.getSessions().values()) {
            Player player = Bukkit.getPlayer(session.getPlayerUuid());
            if (player != null && player.isOnline()) {
                if (game.getBossBar() != null) {
                    player.hideBossBar(game.getBossBar());
                }
                playerStateManager.restore(player, true);
            }
        }

        herdManager.cleanUpArena(arena.getId(), world);
        scoreManager.resetArena(arena.getId());
    }

    /**
     * Absolute cleanup on plugin shutdown.
     */
    public void cleanUpAll() {
        for (String arenaId : new ArrayList<>(activeGames.keySet())) {
            stopGame(arenaId);
        }
        herdManager.cleanUpAll();
        scoreManager.cleanUpAll();
    }
}
