package fr.danakube.danaevent.modules.deacoudre.task;

import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.deacoudre.manager.DacGameManager;
import fr.danakube.danaevent.modules.deacoudre.manager.DacPoolManager;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacGame;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameFormat;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameState;
import fr.danakube.danaevent.modules.deacoudre.model.DacPlayerSession;
import fr.danakube.danaevent.modules.deacoudre.model.DacTeamLifeMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically updates the sidebar scoreboard displaying live visual hearts and pool water counts.
 */
public class DacHudTask extends BukkitRunnable {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Component SIDEBAR_TITLE = MM.deserialize("<bold><aqua>DÉ À COUDRE</aqua></bold>");

    private final DacGameManager gameManager;
    private final DacPoolManager poolManager;
    private final Map<UUID, Scoreboard> playerScoreboards = new ConcurrentHashMap<>();

    public DacHudTask(@NotNull DacGameManager gameManager, @NotNull DacPoolManager poolManager) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager cannot be null");
        this.poolManager = Objects.requireNonNull(poolManager, "poolManager cannot be null");
    }

    @Override
    public void run() {
        for (DacGame game : gameManager.getActiveGames().values()) {
            if (game.getState() != DacGameState.IN_GAME) {
                continue;
            }

            updateGameScoreboard(game);
        }
    }

    private void updateGameScoreboard(@NotNull DacGame game) {
        DacArena arena = game.getArena();
        World world = arena.getLobbyLocation() != null ? arena.getLobbyLocation().getWorld() : Bukkit.getWorlds().get(0);
        int remainingWater = world != null ? poolManager.getRemainingWaterCount(arena, world) : 0;

        List<Component> lines = buildScoreboardLines(game, remainingWater);

        for (UUID playerUuid : game.getParticipants().keySet()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                continue;
            }

            Scoreboard scoreboard = getOrCreateScoreboard(player);
            Objective objective = scoreboard.getObjective("dac_hud");
            if (objective == null) {
                objective = scoreboard.registerNewObjective("dac_hud", Criteria.DUMMY, SIDEBAR_TITLE);
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            } else {
                objective.displayName(SIDEBAR_TITLE);
            }

            // Clear old entries
            for (String entry : scoreboard.getEntries()) {
                scoreboard.resetScores(entry);
            }

            // Populate lines in descending order
            int score = lines.size();
            for (Component line : lines) {
                String legacyOrText = MiniMessage.miniMessage().serialize(line);
                // Scoreboard entries must be unique strings
                String uniqueEntry = createUniqueEntry(legacyOrText, score);
                objective.getScore(uniqueEntry).setScore(score);
                score--;
            }

            if (player.getScoreboard() != scoreboard) {
                player.setScoreboard(scoreboard);
            }
        }
    }

    private List<Component> buildScoreboardLines(@NotNull DacGame game, int remainingWater) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(MM.deserialize("<gray>Arène : <white>" + game.getArena().getDisplayName() + "</white></gray>"));
        lines.add(MM.deserialize("<gray>Tour : <yellow>" + game.getCurrentRound() + "</yellow></gray>"));
        lines.add(MM.deserialize("<dark_gray>-----------------</dark_gray>"));

        int maxLives = game.getArena().getInitialLives();

        if (game.getArena().getFormat() == DacGameFormat.TEAM && game.getArena().getTeamLifeMode() == DacTeamLifeMode.SHARED_POOL) {
            // Display team lives
            for (UUID teamUuid : game.getAliveTeams()) {
                int lives = game.getTeamLives(teamUuid);
                lines.add(MM.deserialize("<white>Équipe : </white>" + formatHearts(lives, maxLives)));
            }
        } else {
            // Display player lives
            for (DacPlayerSession session : game.getParticipants().values()) {
                Player p = Bukkit.getPlayer(session.getPlayerUuid());
                String pName = p != null ? p.getName() : "Joueur";
                lines.add(MM.deserialize("<white>" + pName + " : </white>" + formatHearts(session.getLives(), maxLives)));
            }
        }

        lines.add(MM.deserialize("<dark_gray>-----------------</dark_gray>"));
        lines.add(MM.deserialize("<aqua>Eau restante : <white>" + remainingWater + "</white></aqua>"));
        return lines;
    }

    public static @NotNull String formatHearts(int currentLives, int maxLives) {
        if (currentLives <= 0) {
            return "<dark_gray>ÉLIMINÉ</dark_gray>";
        }

        StringBuilder sb = new StringBuilder("<red>");
        int alive = Math.min(currentLives, maxLives);
        for (int i = 0; i < alive; i++) {
            sb.append("❤");
        }
        sb.append("</red>");

        if (alive < maxLives) {
            sb.append("<gray>");
            for (int i = 0; i < (maxLives - alive); i++) {
                sb.append("❤");
            }
            sb.append("</gray>");
        }

        return sb.toString();
    }

    private @NotNull Scoreboard getOrCreateScoreboard(@NotNull Player player) {
        return playerScoreboards.computeIfAbsent(player.getUniqueId(), k -> {
            ScoreboardManager sm = Bukkit.getScoreboardManager();
            return sm != null ? sm.getNewScoreboard() : null;
        });
    }

    private @NotNull String createUniqueEntry(@NotNull String text, int index) {
        // Strip tags for raw display or keep short to fit 40 chars
        String clean = text.replaceAll("<[^>]*>", "");
        if (clean.length() > 32) {
            clean = clean.substring(0, 32);
        }
        // Append invisible color code combinations to ensure uniqueness per slot
        return clean + "§" + (index % 10);
    }

    /**
     * Resets a player's scoreboard back to main server scoreboard.
     */
    public void resetPlayerScoreboard(@NotNull Player player) {
        playerScoreboards.remove(player.getUniqueId());
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm != null && player.isOnline()) {
            player.setScoreboard(sm.getMainScoreboard());
        }
    }

    /**
     * Cleans up all scoreboards.
     */
    public void cleanUp() {
        for (UUID uuid : playerScoreboards.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                resetPlayerScoreboard(p);
            }
        }
        playerScoreboards.clear();
    }
}
