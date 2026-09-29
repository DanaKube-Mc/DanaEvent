package fr.danakube.danaevent.core.team.manager;

import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages Bukkit Scoreboard teams for DanaTeams, ensuring team color synchronization,
 * glowing outline effects, and tablist glow display.
 */
public class TeamScoreboardManager {

    public static final String TEAM_PREFIX = "det_";

    private final Scoreboard scoreboard;

    public TeamScoreboardManager(@NotNull Scoreboard scoreboard) {
        this.scoreboard = Objects.requireNonNull(scoreboard, "Scoreboard cannot be null");
    }

    public TeamScoreboardManager() {
        this(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public @NotNull Scoreboard getScoreboard() {
        return scoreboard;
    }

    public static @NotNull String getBukkitTeamName(@NotNull String teamId) {
        String cleanId = teamId.trim().toLowerCase();
        return TEAM_PREFIX + cleanId;
    }

    /**
     * Registers or retrieves a Bukkit scoreboard team for the given DanaTeam.
     * Synchronizes display name, color, and friendly invisible settings.
     *
     * @param team the DanaTeam to register
     * @return the configured Bukkit Team
     */
    public @NotNull Team registerTeam(@NotNull DanaTeam team) {
        Objects.requireNonNull(team, "team cannot be null");
        String teamName = getBukkitTeamName(team.getId());

        Team bukkitTeam = scoreboard.getTeam(teamName);
        if (bukkitTeam == null) {
            bukkitTeam = scoreboard.registerNewTeam(teamName);
        }

        bukkitTeam.displayName(Component.text(team.getDisplayName()));
        bukkitTeam.color(team.getColor().getNamedTextColor());
        bukkitTeam.setAllowFriendlyFire(false);
        bukkitTeam.setCanSeeFriendlyInvisibles(true);

        // Add any online members
        for (UUID memberUuid : team.getMembers().keySet()) {
            Player player = Bukkit.getPlayer(memberUuid);
            if (player != null && !bukkitTeam.hasPlayer(player)) {
                bukkitTeam.addPlayer(player);
            }
        }

        return bukkitTeam;
    }

    /**
     * Updates the Bukkit scoreboard team's color to match the DanaTeam.
     *
     * @param team the DanaTeam whose color has changed
     */
    public void updateTeamColor(@NotNull DanaTeam team) {
        Objects.requireNonNull(team, "team cannot be null");
        String teamName = getBukkitTeamName(team.getId());
        Team bukkitTeam = scoreboard.getTeam(teamName);
        if (bukkitTeam != null) {
            bukkitTeam.color(team.getColor().getNamedTextColor());
        } else {
            registerTeam(team);
        }
    }

    /**
     * Adds a player to the Bukkit scoreboard team corresponding to the DanaTeam.
     *
     * @param team   the DanaTeam
     * @param player the Player to add
     */
    public void addPlayer(@NotNull DanaTeam team, @NotNull Player player) {
        Objects.requireNonNull(team, "team cannot be null");
        Objects.requireNonNull(player, "player cannot be null");

        // Remove from any prior team first
        removePlayer(player);

        Team bukkitTeam = registerTeam(team);
        if (!bukkitTeam.hasPlayer(player)) {
            bukkitTeam.addPlayer(player);
        }
    }

    /**
     * Removes a player from a specific Bukkit scoreboard team.
     *
     * @param teamId the ID of the team
     * @param player the Player to remove
     */
    public void removePlayer(@NotNull String teamId, @NotNull Player player) {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(player, "player cannot be null");

        Team bukkitTeam = scoreboard.getTeam(getBukkitTeamName(teamId));
        if (bukkitTeam != null) {
            bukkitTeam.removePlayer(player);
        }
    }

    /**
     * Removes a player from all DanaTeam-managed Bukkit scoreboard teams.
     *
     * @param player the Player to remove
     */
    public void removePlayer(@NotNull Player player) {
        Objects.requireNonNull(player, "player cannot be null");
        for (Team t : scoreboard.getTeams()) {
            if (t.getName().startsWith(TEAM_PREFIX) && t.hasPlayer(player)) {
                t.removePlayer(player);
            }
        }
    }

    /**
     * Unregisters the Bukkit scoreboard team for the given team ID.
     *
     * @param teamId the team ID to unregister
     */
    public void unregisterTeam(@NotNull String teamId) {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Team bukkitTeam = scoreboard.getTeam(getBukkitTeamName(teamId));
        if (bukkitTeam != null) {
            for (String entry : new ArrayList<>(bukkitTeam.getEntries())) {
                bukkitTeam.removeEntry(entry);
            }
            bukkitTeam.unregister();
        }
    }

    /**
     * Retrieves the Bukkit scoreboard team associated with a DanaTeam ID if it exists.
     *
     * @param teamId the team ID
     * @return Optional containing the Bukkit Team, or empty
     */
    public @NotNull Optional<Team> getBukkitTeam(@Nullable String teamId) {
        if (teamId == null || teamId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(scoreboard.getTeam(getBukkitTeamName(teamId)));
    }

    /**
     * Enables or disables glowing on a player.
     *
     * @param player  the player
     * @param glowing true to enable glowing, false otherwise
     */
    public void setGlowing(@NotNull Player player, boolean glowing) {
        Objects.requireNonNull(player, "player cannot be null");
        player.setGlowing(glowing);
    }

    /**
     * Checks if a player has glowing enabled.
     *
     * @param player the player to check
     * @return true if glowing, false otherwise
     */
    public boolean isGlowing(@NotNull Player player) {
        Objects.requireNonNull(player, "player cannot be null");
        return player.isGlowing();
    }

    /**
     * Cleans up all Bukkit teams registered by the TeamScoreboardManager.
     */
    public void cleanUp() {
        for (Team t : new ArrayList<>(scoreboard.getTeams())) {
            if (t.getName().startsWith(TEAM_PREFIX)) {
                for (String entry : new ArrayList<>(t.getEntries())) {
                    t.removeEntry(entry);
                }
                t.unregister();
            }
        }
    }
}
