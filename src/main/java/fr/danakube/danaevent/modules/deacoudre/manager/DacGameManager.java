package fr.danakube.danaevent.modules.deacoudre.manager;

import fr.danakube.danaevent.core.player.PlayerStateManager;
import fr.danakube.danaevent.core.team.manager.TeamManager;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.modules.deacoudre.database.DeACoudreDatabase;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacGame;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameFormat;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameState;
import fr.danakube.danaevent.modules.deacoudre.model.DacPlayerSession;
import fr.danakube.danaevent.modules.deacoudre.model.DacTeamLifeMode;
import fr.danakube.danaevent.modules.deacoudre.model.JumpMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Orchestrates the full Dé à Coudre gameplay cycle, turn/wave management,
 * player sessions, team modes, victory conditions, and persistence.
 */
public class DacGameManager implements DacJumpCallback {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<DyeColor> AVAILABLE_COLORS = List.of(
        DyeColor.RED, DyeColor.BLUE, DyeColor.GREEN, DyeColor.YELLOW,
        DyeColor.PURPLE, DyeColor.ORANGE, DyeColor.CYAN, DyeColor.LIGHT_BLUE,
        DyeColor.LIME, DyeColor.MAGENTA, DyeColor.PINK, DyeColor.BROWN,
        DyeColor.GRAY, DyeColor.LIGHT_GRAY, DyeColor.WHITE, DyeColor.BLACK
    );

    private final Plugin plugin;
    private final PlayerStateManager playerStateManager;
    private final DacArenaManager arenaManager;
    private final DacPoolManager poolManager;
    private final DeACoudreDatabase database;
    private final TeamManager teamManager;

    private final Map<String, DacGame> activeGames = new ConcurrentHashMap<>();
    private Consumer<DacGame> turnStartListener;
    private Consumer<DacGame> gameEndListener;

    public DacGameManager(
        @NotNull Plugin plugin,
        @NotNull PlayerStateManager playerStateManager,
        @NotNull DacArenaManager arenaManager,
        @NotNull DacPoolManager poolManager,
        @NotNull DeACoudreDatabase database,
        @Nullable TeamManager teamManager
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.playerStateManager = Objects.requireNonNull(playerStateManager, "playerStateManager cannot be null");
        this.arenaManager = Objects.requireNonNull(arenaManager, "arenaManager cannot be null");
        this.poolManager = Objects.requireNonNull(poolManager, "poolManager cannot be null");
        this.database = Objects.requireNonNull(database, "database cannot be null");
        this.teamManager = teamManager;
    }

    public void setTurnStartListener(@Nullable Consumer<DacGame> turnStartListener) {
        this.turnStartListener = turnStartListener;
    }

    public void setGameEndListener(@Nullable Consumer<DacGame> gameEndListener) {
        this.gameEndListener = gameEndListener;
    }

    public @Nullable DacGame getGame(@NotNull String arenaId) {
        return activeGames.get(arenaId.trim().toLowerCase());
    }

    public @NotNull Map<String, DacGame> getActiveGames() {
        return activeGames;
    }

    public Optional<DacPlayerSession> getSession(@NotNull UUID playerUuid) {
        for (DacGame game : activeGames.values()) {
            DacPlayerSession session = game.getSession(playerUuid);
            if (session != null) {
                return Optional.of(session);
            }
        }
        return Optional.empty();
    }

    public Optional<DacArena> getPlayerArena(@NotNull UUID playerUuid) {
        for (DacGame game : activeGames.values()) {
            if (game.getParticipants().containsKey(playerUuid)) {
                return Optional.of(game.getArena());
            }
        }
        return Optional.empty();
    }

    /**
     * Enrolls a player into an arena game lobby.
     */
    public boolean joinGame(@NotNull Player player, @NotNull String arenaId) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(arenaId, "arenaId cannot be null");

        if (playerStateManager.hasSnapshot(player.getUniqueId()) || getSession(player.getUniqueId()).isPresent()) {
            return false;
        }

        Optional<DacArena> arenaOpt = arenaManager.getArena(arenaId);
        if (arenaOpt.isEmpty()) {
            return false;
        }

        DacArena arena = arenaOpt.get();
        if (!arena.isEnabled() || !arena.isReady()) {
            return false;
        }

        DacGame game = activeGames.computeIfAbsent(arena.getId().trim().toLowerCase(), k -> new DacGame(arena));
        if (game.getState() != DacGameState.WAITING && game.getState() != DacGameState.STARTING) {
            return false;
        }

        DyeColor assignedColor = null;
        UUID holderUuid = player.getUniqueId();
        boolean isTeam = false;

        if (arena.getFormat() == DacGameFormat.TEAM) {
            if (teamManager == null) {
                player.sendMessage(MM.deserialize("<red>Le mode équipe n'est pas disponible actuellement.</red>"));
                return false;
            }
            Optional<DanaTeam> teamOpt = teamManager.getPlayerTeam(player.getUniqueId());
            if (teamOpt.isEmpty()) {
                player.sendMessage(MM.deserialize("<red>Vous devez appartenir à une équipe pour rejoindre cette arène !</red>"));
                return false;
            }
            DanaTeam team = teamOpt.get();
            final UUID teamUuid = UUID.nameUUIDFromBytes(("team:" + team.getId()).getBytes(StandardCharsets.UTF_8));
            holderUuid = teamUuid;
            isTeam = true;
            try {
                assignedColor = DyeColor.valueOf(team.getColor().name());
            } catch (IllegalArgumentException e) {
                assignedColor = DyeColor.BLUE;
            }

            if (arena.getTeamLifeMode() == DacTeamLifeMode.SHARED_POOL) {
                if (!game.getParticipants().values().stream().anyMatch(s -> s.getEffectiveHolderUuid().equals(teamUuid))) {
                    game.setTeamLives(teamUuid, arena.getInitialLives());
                }
            }
        } else {
            for (DyeColor color : AVAILABLE_COLORS) {
                if (!game.isColorUsed(color)) {
                    assignedColor = color;
                    break;
                }
            }
            if (assignedColor == null) {
                assignedColor = DyeColor.WHITE;
            }
        }

        final UUID effectiveHolderUuid = holderUuid;

        // Save inventory and player state
        playerStateManager.saveAndClear(player);

        DacPlayerSession session = new DacPlayerSession(
            player.getUniqueId(),
            arena.getId(),
            assignedColor,
            effectiveHolderUuid,
            isTeam,
            arena.getInitialLives()
        );
        game.addParticipant(session);

        if (arena.getLobbyLocation() != null) {
            player.teleport(arena.getLobbyLocation());
        }

        broadcastToGame(game, "<gradient:#00c6ff:#0072ff><b>Dé à Coudre</b></gradient> <dark_gray>»</dark_gray> <gray>"
            + player.getName() + " a rejoint la partie ! (" + game.getParticipants().size() + " joueur(s))</gray>");

        return true;
    }

    /**
     * Removes a player from an arena game and restores their original inventory.
     */
    public boolean leaveGame(@NotNull Player player) {
        Objects.requireNonNull(player, "player cannot be null");

        Optional<DacArena> arenaOpt = getPlayerArena(player.getUniqueId());
        if (arenaOpt.isEmpty()) {
            return false;
        }

        DacGame game = getGame(arenaOpt.get().getId());
        if (game == null) {
            return false;
        }

        UUID playerUuid = player.getUniqueId();
        boolean wasJumper = playerUuid.equals(game.getCurrentJumper());

        game.removeParticipant(playerUuid);
        playerStateManager.restore(player, true);

        broadcastToGame(game, "<gradient:#00c6ff:#0072ff><b>Dé à Coudre</b></gradient> <dark_gray>»</dark_gray> <gray>"
            + player.getName() + " a quitté la partie.</gray>");

        if (game.getState() == DacGameState.IN_GAME) {
            if (checkWinCondition(game)) {
                return true;
            }
            if (wasJumper && game.getArena().getJumpMode() == JumpMode.TURN_BY_TURN) {
                startNextTurn(game);
            }
        } else if (game.getParticipants().isEmpty()) {
            activeGames.remove(game.getArena().getId().trim().toLowerCase());
        }

        return true;
    }

    /**
     * Starts a Dé à Coudre match on the specified arena.
     */
    public boolean startGame(@NotNull String arenaId) {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");

        DacGame game = getGame(arenaId);
        if (game == null) {
            return false;
        }

        DacArena arena = game.getArena();
        if (game.getState() == DacGameState.IN_GAME || game.getState() == DacGameState.ENDED) {
            return false;
        }

        if (game.getParticipants().size() < 2) {
            // Check if solo test with 1 player or team requirements
            if (arena.getFormat() == DacGameFormat.TEAM && game.getAliveTeams().size() < 2) {
                return false;
            }
            if (arena.getFormat() == DacGameFormat.SOLO && game.getParticipants().isEmpty()) {
                return false;
            }
        }

        World world = arena.getLobbyLocation() != null ? arena.getLobbyLocation().getWorld() : Bukkit.getWorlds().get(0);
        if (world != null) {
            poolManager.captureSnapshot(arena, world);
        }

        game.setState(DacGameState.IN_GAME);
        game.initializeTurnQueue();

        broadcastToGame(game, "<gradient:#00c6ff:#0072ff><b>Dé à Coudre</b></gradient> <dark_gray>»</dark_gray> <green>La partie commence ! Bonne chance à tous les sauteurs !</green>");

        if (arena.getJumpMode() == JumpMode.TURN_BY_TURN) {
            startNextTurn(game);
        } else {
            startWave(game);
        }

        return true;
    }

    /**
     * Advances to and triggers the next player's jump turn.
     */
    public void startNextTurn(@NotNull DacGame game) {
        if (game.getState() != DacGameState.IN_GAME) {
            return;
        }

        if (checkWinCondition(game)) {
            return;
        }

        UUID nextJumperUuid = game.advanceNextJumper();
        if (nextJumperUuid == null) {
            checkWinCondition(game);
            return;
        }

        Player jumper = Bukkit.getPlayer(nextJumperUuid);
        DacPlayerSession session = game.getSession(nextJumperUuid);
        if (jumper == null || session == null || !session.isAlive()) {
            startNextTurn(game);
            return;
        }

        session.setJumping(true);
        if (game.getArena().getDivingLocation() != null) {
            jumper.teleport(game.getArena().getDivingLocation());
        }

        jumper.showTitle(Title.title(
            MM.deserialize("<gold>À votre tour !</gold>"),
            MM.deserialize("<gray>Élancez-vous dans le bassin !</gray>"),
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(300))
        ));

        broadcastToGame(game, "<gradient:#00c6ff:#0072ff><b>Tour " + game.getCurrentRound() + "</b></gradient> <dark_gray>»</dark_gray> <yellow>"
            + jumper.getName() + "</yellow> s'élance sur le plongeoir !");

        if (turnStartListener != null) {
            turnStartListener.accept(game);
        }
    }

    /**
     * Starts a simultaneous jump wave for all surviving participants.
     */
    public void startWave(@NotNull DacGame game) {
        if (game.getState() != DacGameState.IN_GAME) {
            return;
        }

        if (checkWinCondition(game)) {
            return;
        }

        game.resetWave();
        Location diving = game.getArena().getDivingLocation();

        for (UUID playerUuid : game.getAlivePlayers()) {
            Player player = Bukkit.getPlayer(playerUuid);
            DacPlayerSession session = game.getSession(playerUuid);
            if (player != null && session != null) {
                session.setJumping(true);
                if (diving != null) {
                    player.teleport(diving);
                }
                player.showTitle(Title.title(
                    MM.deserialize("<gradient:#ff416c:#ff4b2b><b>Vague " + game.getWaveNumber() + " !</b></gradient>"),
                    MM.deserialize("<white>Tous à l'eau !</white>"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(300))
                ));
            }
        }

        broadcastToGame(game, "<gradient:#00c6ff:#0072ff><b>Vague " + game.getWaveNumber() + "</b></gradient> <dark_gray>»</dark_gray> <green>C'est parti pour le saut simultané !</green>");

        if (turnStartListener != null) {
            turnStartListener.accept(game);
        }
    }

    @Override
    public void onJumpSuccess(@NotNull Player player, @NotNull Block waterBlock, boolean isPerfect) {
        Optional<DacArena> arenaOpt = getPlayerArena(player.getUniqueId());
        if (arenaOpt.isEmpty()) {
            return;
        }

        DacGame game = getGame(arenaOpt.get().getId());
        if (game == null || game.getState() != DacGameState.IN_GAME) {
            return;
        }

        DacPlayerSession session = game.getSession(player.getUniqueId());
        if (session == null) {
            return;
        }

        session.setJumping(false);
        session.incrementSuccessfulJumps();

        if (isPerfect) {
            session.incrementPerfectDacs();
            if (game.getArena().getTeamLifeMode() == DacTeamLifeMode.SHARED_POOL) {
                game.incrementTeamLife(session.getEffectiveHolderUuid(), game.getArena().getInitialLives());
            } else {
                session.incrementLife(game.getArena().getInitialLives());
            }

            broadcastToGame(game, "<gradient:#FFD700:#FFA500><b>DÉ À COUDRE PARFAIT !</b></gradient> <white>"
                + player.getName() + " a réussi un saut légendaire et regagne une vie !</white>");

            try {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            } catch (Throwable ignored) {
            }
        } else {
            player.sendMessage(MM.deserialize("<green>✔ Saut réussi !</green>"));
        }

        if (game.getArena().getLobbyLocation() != null) {
            player.teleport(game.getArena().getLobbyLocation());
        }

        handleAfterJump(game, player.getUniqueId());
    }

    @Override
    public void onJumpFail(@NotNull Player player, @NotNull String reason) {
        Optional<DacArena> arenaOpt = getPlayerArena(player.getUniqueId());
        if (arenaOpt.isEmpty()) {
            return;
        }

        DacGame game = getGame(arenaOpt.get().getId());
        if (game == null || game.getState() != DacGameState.IN_GAME) {
            return;
        }

        DacPlayerSession session = game.getSession(player.getUniqueId());
        if (session == null) {
            return;
        }

        session.setJumping(false);

        if (game.getArena().getTeamLifeMode() == DacTeamLifeMode.SHARED_POOL) {
            game.decrementTeamLife(session.getEffectiveHolderUuid());
            int teamLives = game.getTeamLives(session.getEffectiveHolderUuid());
            player.sendMessage(MM.deserialize("<red>Plouf raté ! Vies restantes de votre équipe : " + teamLives + "</red>"));
            if (teamLives <= 0) {
                broadcastToGame(game, "<red>L'équipe de " + player.getName() + " a épuisé ses vies et est éliminée !</red>");
            }
        } else {
            session.decrementLife();
            player.sendMessage(MM.deserialize("<red>Plouf raté ! Vies restantes : " + session.getLives() + "</red>"));
            if (session.getLives() <= 0) {
                session.setSpectator(true);
                player.setGameMode(GameMode.SPECTATOR);
                broadcastToGame(game, "<gradient:#ff416c:#ff4b2b><b>Dé à Coudre</b></gradient> <dark_gray>»</dark_gray> <red>"
                    + player.getName() + " est éliminé !</red>");
            }
        }

        if (game.getArena().getLobbyLocation() != null) {
            player.teleport(game.getArena().getLobbyLocation());
        }

        handleAfterJump(game, player.getUniqueId());
    }

    /**
     * Handles timeout when countdown expires without player jumping.
     */
    public void handleTimeout(@NotNull Player player) {
        onJumpFail(player, "Temps de saut écoulé !");
    }

    private void handleAfterJump(@NotNull DacGame game, @NotNull UUID playerUuid) {
        if (game.getArena().getJumpMode() == JumpMode.SIMULTANEOUS_WAVE) {
            game.recordWaveJump(playerUuid);
            if (game.isWaveComplete()) {
                if (!checkWinCondition(game)) {
                    game.incrementWaveNumber();
                    startWave(game);
                }
            }
        } else {
            if (!checkWinCondition(game)) {
                startNextTurn(game);
            }
        }
    }

    /**
     * Evaluates winning conditions. Returns true if match concluded.
     */
    public boolean checkWinCondition(@NotNull DacGame game) {
        if (game.getState() != DacGameState.IN_GAME) {
            return false;
        }

        if (game.getArena().getFormat() == DacGameFormat.TEAM) {
            List<UUID> aliveTeams = game.getAliveTeams();
            if (aliveTeams.size() <= 1) {
                UUID winningTeam = aliveTeams.isEmpty() ? null : aliveTeams.get(0);
                endGame(game, winningTeam, true);
                return true;
            }
        } else {
            List<UUID> alivePlayers = game.getAlivePlayers();
            if (alivePlayers.size() <= 1) {
                UUID winnerUuid = alivePlayers.isEmpty() ? null : alivePlayers.get(0);
                endGame(game, winnerUuid, false);
                return true;
            }
        }

        return false;
    }

    /**
     * Concludes an active match, rewards winner, records stats, restores state, and rolls back pool.
     */
    public void endGame(@NotNull DacGame game, @Nullable UUID winnerUuid, boolean isTeam) {
        game.setState(DacGameState.ENDED);

        String winnerName = "Personne";
        if (winnerUuid != null) {
            if (isTeam && teamManager != null) {
                for (DanaTeam team : teamManager.getTeams()) {
                    UUID tUuid = UUID.nameUUIDFromBytes(("team:" + team.getId()).getBytes(StandardCharsets.UTF_8));
                    if (tUuid.equals(winnerUuid)) {
                        winnerName = "L'équipe " + team.getDisplayName();
                        break;
                    }
                }
            } else {
                Player winnerPlayer = Bukkit.getPlayer(winnerUuid);
                winnerName = winnerPlayer != null ? winnerPlayer.getName() : winnerUuid.toString();
            }
        }

        Component winMessage = MM.deserialize("<gradient:#00c6ff:#0072ff><b>VICTOIRE !</b></gradient> <gold>"
            + winnerName + "</gold> <green>remporte la partie de Dé à Coudre !</green>");
        broadcastToGame(game, winMessage);

        // Asynchronously persist match records for all participants
        for (DacPlayerSession session : game.getParticipants().values()) {
            boolean isWinner = winnerUuid != null && (isTeam ? session.getEffectiveHolderUuid().equals(winnerUuid) : session.getPlayerUuid().equals(winnerUuid));
            database.recordMatchResult(
                game.getArena().getId(),
                session.getEffectiveHolderUuid(),
                session.isTeam(),
                isWinner,
                session.getSuccessfulJumps(),
                session.getPerfectDacs()
            );
        }

        // Restore players inventories and states
        for (UUID playerUuid : new ArrayList<>(game.getParticipants().keySet())) {
            Player p = Bukkit.getPlayer(playerUuid);
            if (p != null) {
                p.setGameMode(GameMode.ADVENTURE);
                playerStateManager.restore(p, true);
            }
        }

        // Clean rollback of the pool
        World world = game.getArena().getLobbyLocation() != null ? game.getArena().getLobbyLocation().getWorld() : Bukkit.getWorlds().get(0);
        if (world != null) {
            poolManager.rollbackPool(game.getArena(), world);
        }

        if (gameEndListener != null) {
            gameEndListener.accept(game);
        }

        activeGames.remove(game.getArena().getId().trim().toLowerCase());
    }

    /**
     * Forcibly stops an active game and cleans up arena.
     */
    public void stopGame(@NotNull String arenaId) {
        DacGame game = activeGames.remove(arenaId.trim().toLowerCase());
        if (game == null) {
            return;
        }

        game.setState(DacGameState.ENDED);
        broadcastToGame(game, "<red>La partie de Dé à Coudre a été interrompue par un administrateur.</red>");

        for (UUID playerUuid : new ArrayList<>(game.getParticipants().keySet())) {
            Player p = Bukkit.getPlayer(playerUuid);
            if (p != null) {
                p.setGameMode(GameMode.ADVENTURE);
                playerStateManager.restore(p, true);
            }
        }

        World world = game.getArena().getLobbyLocation() != null ? game.getArena().getLobbyLocation().getWorld() : Bukkit.getWorlds().get(0);
        if (world != null) {
            poolManager.rollbackPool(game.getArena(), world);
        }

        if (gameEndListener != null) {
            gameEndListener.accept(game);
        }
    }

    /**
     * Cleans up all active games upon server stop or module reload.
     */
    public void cleanUpAll() {
        for (String arenaId : new ArrayList<>(activeGames.keySet())) {
            stopGame(arenaId);
        }
        activeGames.clear();
    }

    private void broadcastToGame(@NotNull DacGame game, @NotNull String message) {
        broadcastToGame(game, MM.deserialize(message));
    }

    private void broadcastToGame(@NotNull DacGame game, @NotNull Component message) {
        for (UUID playerUuid : game.getParticipants().keySet()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }
}
