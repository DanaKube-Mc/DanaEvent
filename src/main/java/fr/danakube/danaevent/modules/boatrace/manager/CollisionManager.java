package fr.danakube.danaevent.modules.boatrace.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Manages the scoreboard team to disable collisions between boat racers.
 */
public class CollisionManager {

    public static final String TEAM_NAME = "br_nocollide";

    private final Scoreboard scoreboard;

    public CollisionManager(Scoreboard scoreboard) {
        this.scoreboard = Objects.requireNonNull(scoreboard, "Scoreboard cannot be null");
        getOrCreateTeam();
    }

    public CollisionManager() {
        this(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private Team getOrCreateTeam() {
        Team current = scoreboard.getTeam(TEAM_NAME);
        if (current == null) {
            current = scoreboard.registerNewTeam(TEAM_NAME);
            current.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            current.setCanSeeFriendlyInvisibles(false);
        }
        return current;
    }

    /**
     * Adds a player to the collision-free race team.
     *
     * @param player the player to add
     */
    public void addPlayer(Player player) {
        if (player == null) {
            return;
        }
        Team activeTeam = getOrCreateTeam();
        activeTeam.addPlayer(player);
    }

    /**
     * Removes a player from the collision-free race team.
     *
     * @param player the player to remove
     */
    public void removePlayer(Player player) {
        if (player == null) {
            return;
        }
        Team activeTeam = scoreboard.getTeam(TEAM_NAME);
        if (activeTeam != null) {
            activeTeam.removePlayer(player);
        }
    }

    /**
     * Checks if a player is registered in the collision-free race team.
     *
     * @param player the player to check
     * @return true if in team, false otherwise
     */
    public boolean hasPlayer(Player player) {
        if (player == null) {
            return false;
        }
        Team activeTeam = scoreboard.getTeam(TEAM_NAME);
        if (activeTeam == null) {
            return false;
        }
        return activeTeam.hasPlayer(player);
    }

    /**
     * Cleans up all team entries and unregisters the team.
     */
    public void cleanUp() {
        Team activeTeam = scoreboard.getTeam(TEAM_NAME);
        if (activeTeam != null) {
            for (String entry : new ArrayList<>(activeTeam.getEntries())) {
                activeTeam.removeEntry(entry);
            }
            activeTeam.unregister();
        }
    }

    public Team getTeam() {
        return scoreboard.getTeam(TEAM_NAME);
    }
}
