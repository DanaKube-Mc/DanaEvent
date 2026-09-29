package fr.danakube.danaevent.core.team.manager;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.team.database.TeamDatabase;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import fr.danakube.danaevent.core.team.model.TeamInvite;
import fr.danakube.danaevent.core.team.model.TeamMember;
import fr.danakube.danaevent.core.team.model.TeamRole;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Manages event teams, members, dynamic permissions, color uniqueness, and pending invitations.
 */
public class TeamManager {

    public static final Pattern TEAM_ID_PATTERN = Pattern.compile("^[a-z0-9_-]{3,32}$");

    private final DanaEventPlugin plugin;
    private final TeamDatabase database;

    private final Map<String, DanaTeam> teams = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerTeamMap = new ConcurrentHashMap<>();
    private final Map<TeamColor, String> colorTeamMap = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, TeamInvite>> pendingInvites = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> playerAttachments = new ConcurrentHashMap<>();

    private final Object colorLock = new Object();

    public TeamManager(@NotNull DanaEventPlugin plugin, @NotNull TeamDatabase database) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.database = Objects.requireNonNull(database, "database cannot be null");
    }

    public @NotNull TeamDatabase getDatabase() {
        return database;
    }

    /**
     * Asynchronously loads all teams and members from the database into the memory cache.
     *
     * @return CompletableFuture completing when loaded
     */
    public CompletableFuture<Void> loadAllTeams() {
        return database.loadAllTeams().thenAccept(loaded -> {
            synchronized (colorLock) {
                teams.clear();
                playerTeamMap.clear();
                colorTeamMap.clear();

                for (DanaTeam team : loaded) {
                    teams.put(team.getId(), team);
                    colorTeamMap.put(team.getColor(), team.getId());

                    for (UUID memberUuid : team.getMembers().keySet()) {
                        playerTeamMap.put(memberUuid, team.getId());
                    }
                }
            }

            // Apply permissions for online players
            if (plugin.isEnabled()) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    handlePlayerJoin(player);
                }
            }
        });
    }

    public @NotNull Collection<DanaTeam> getTeams() {
        return Collections.unmodifiableCollection(teams.values());
    }

    public @NotNull Optional<DanaTeam> getTeam(@Nullable String teamId) {
        if (teamId == null || teamId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(teams.get(teamId.trim().toLowerCase()));
    }

    public @NotNull Optional<DanaTeam> getPlayerTeam(@Nullable UUID playerUuid) {
        if (playerUuid == null) {
            return Optional.empty();
        }
        String teamId = playerTeamMap.get(playerUuid);
        return teamId != null ? getTeam(teamId) : Optional.empty();
    }

    public boolean hasTeam(@Nullable UUID playerUuid) {
        return playerUuid != null && playerTeamMap.containsKey(playerUuid);
    }

    public boolean isColorAvailable(@NotNull TeamColor color) {
        Objects.requireNonNull(color, "color cannot be null");
        synchronized (colorLock) {
            return !colorTeamMap.containsKey(color);
        }
    }

    public @NotNull Optional<DanaTeam> getTeamByColor(@NotNull TeamColor color) {
        Objects.requireNonNull(color, "color cannot be null");
        synchronized (colorLock) {
            String teamId = colorTeamMap.get(color);
            return teamId != null ? getTeam(teamId) : Optional.empty();
        }
    }

    public @NotNull Optional<TeamColor> getFirstAvailableColor() {
        synchronized (colorLock) {
            return Arrays.stream(TeamColor.values())
                .filter(c -> !colorTeamMap.containsKey(c))
                .findFirst();
        }
    }

    /**
     * Creates a new team with strict validation and color reservation.
     *
     * @param id          slug identifier (3-32 chars)
     * @param displayName formatted display name
     * @param color       chosen team color
     * @param leader      player creating the team
     * @return CompletableFuture containing created DanaTeam
     */
    public CompletableFuture<DanaTeam> createTeam(
        @NotNull String id,
        @NotNull String displayName,
        @NotNull TeamColor color,
        @NotNull Player leader
    ) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(color, "color cannot be null");
        Objects.requireNonNull(leader, "leader cannot be null");

        String cleanId = id.trim().toLowerCase();
        if (!TEAM_ID_PATTERN.matcher(cleanId).matches()) {
            throw new IllegalArgumentException("Team ID must match regex ^[a-z0-9_-]{3,32}$");
        }

        UUID leaderUuid = leader.getUniqueId();
        if (hasTeam(leaderUuid)) {
            throw new IllegalStateException("Player is already in a team");
        }

        if (teams.containsKey(cleanId)) {
            throw new IllegalStateException("Team ID already exists: " + cleanId);
        }

        synchronized (colorLock) {
            if (colorTeamMap.containsKey(color)) {
                throw new IllegalStateException("Color already in use: " + color.getId());
            }
            // Tentatively reserve color in memory
            colorTeamMap.put(color, cleanId);
        }

        DanaTeam team = new DanaTeam(cleanId, displayName, color, leaderUuid);
        TeamMember leaderMember = new TeamMember(leaderUuid, leader.getName(), TeamRole.LEADER, Instant.now());
        team.addMember(leaderMember);

        return database.insertTeam(team)
            .thenApply(v -> {
                teams.put(cleanId, team);
                playerTeamMap.put(leaderUuid, cleanId);

                // Apply dynamic permissions
                applyTeamPermissions(leader, cleanId, color);

                plugin.getMessageManager().sendMessage(
                    leader,
                    "team-created",
                    Placeholder.parsed("team", team.getFormattedName())
                );
                return team;
            })
            .exceptionally(ex -> {
                // Rollback tentative color reservation on failure
                synchronized (colorLock) {
                    colorTeamMap.remove(color);
                }
                if (ex instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(ex);
            });
    }

    /**
     * Disbands a team, liberating its color and memberships.
     *
     * @param teamId team identifier
     * @return CompletableFuture completing when disbanded
     */
    public CompletableFuture<Void> disbandTeam(@NotNull String teamId) {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        String cleanId = teamId.trim().toLowerCase();

        DanaTeam team = teams.get(cleanId);
        if (team == null) {
            return CompletableFuture.completedFuture(null);
        }

        return database.deleteTeam(cleanId).thenRun(() -> {
            synchronized (colorLock) {
                teams.remove(cleanId);
                colorTeamMap.remove(team.getColor());

                for (UUID memberUuid : team.getMembers().keySet()) {
                    playerTeamMap.remove(memberUuid);

                    Player player = Bukkit.getPlayer(memberUuid);
                    if (player != null && player.isOnline()) {
                        removeTeamPermissions(player);
                        plugin.getMessageManager().sendMessage(
                            player,
                            "team-disbanded",
                            Placeholder.parsed("team", team.getFormattedName())
                        );
                    }
                }
            }

            // Remove any pending invites referencing this team
            for (Map<String, TeamInvite> targetInvites : pendingInvites.values()) {
                targetInvites.remove(cleanId);
            }
        });
    }

    /**
     * Changes a team's color with thread-safe exclusive check.
     *
     * @param teamId   team identifier
     * @param newColor new TeamColor
     * @return CompletableFuture completing when updated
     */
    public CompletableFuture<Boolean> changeTeamColor(@NotNull String teamId, @NotNull TeamColor newColor) {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(newColor, "newColor cannot be null");

        DanaTeam team = teams.get(teamId.trim().toLowerCase());
        if (team == null) {
            return CompletableFuture.completedFuture(false);
        }

        TeamColor oldColor = team.getColor();
        if (oldColor == newColor) {
            return CompletableFuture.completedFuture(true);
        }

        synchronized (colorLock) {
            if (colorTeamMap.containsKey(newColor)) {
                return CompletableFuture.completedFuture(false);
            }
            colorTeamMap.remove(oldColor);
            colorTeamMap.put(newColor, team.getId());
        }

        team.setColor(newColor);
        return database.updateTeam(team)
            .thenApply(v -> {
                // Update permissions for online members
                for (UUID memberUuid : team.getMembers().keySet()) {
                    Player player = Bukkit.getPlayer(memberUuid);
                    if (player != null && player.isOnline()) {
                        applyTeamPermissions(player, team.getId(), newColor);
                        plugin.getMessageManager().sendMessage(
                            player,
                            "team-color-changed",
                            Placeholder.parsed("color", newColor.getDisplayName())
                        );
                    }
                }
                return true;
            })
            .exceptionally(ex -> {
                // Rollback color in memory
                synchronized (colorLock) {
                    colorTeamMap.remove(newColor);
                    colorTeamMap.put(oldColor, team.getId());
                    team.setColor(oldColor);
                }
                return false;
            });
    }

    /**
     * Sends an invitation to a player to join a team.
     *
     * @param teamId       team identifier
     * @param inviterUuid  UUID of the member sending the invite
     * @param targetPlayer player receiving the invite
     * @return true if invitation successfully sent, false if target cannot be invited
     */
    public boolean invitePlayer(@NotNull String teamId, @NotNull UUID inviterUuid, @NotNull Player targetPlayer) {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(inviterUuid, "inviterUuid cannot be null");
        Objects.requireNonNull(targetPlayer, "targetPlayer cannot be null");

        DanaTeam team = teams.get(teamId.trim().toLowerCase());
        if (team == null || !team.hasMember(inviterUuid)) {
            return false;
        }

        UUID targetUuid = targetPlayer.getUniqueId();
        if (hasTeam(targetUuid)) {
            return false;
        }

        TeamInvite invite = TeamInvite.create(team.getId(), inviterUuid, targetUuid);
        pendingInvites.computeIfAbsent(targetUuid, k -> new ConcurrentHashMap<>()).put(team.getId(), invite);

        Player inviter = Bukkit.getPlayer(inviterUuid);
        String inviterName = inviter != null ? inviter.getName() : "Un membre";

        plugin.getMessageManager().sendMessage(
            targetPlayer,
            "team-invite-received",
            Placeholder.parsed("player", inviterName),
            Placeholder.parsed("team", team.getFormattedName()),
            Placeholder.parsed("team_id", team.getId())
        );

        if (inviter != null && inviter.isOnline()) {
            plugin.getMessageManager().sendMessage(
                inviter,
                "team-invite-sent",
                Placeholder.parsed("player", targetPlayer.getName())
            );
        }

        return true;
    }

    /**
     * Accepts a pending invitation and joins the team.
     *
     * @param player player accepting the invitation
     * @param teamId target team identifier
     * @return CompletableFuture with true if joined, false if invalid or expired
     */
    public CompletableFuture<Boolean> acceptInvite(@NotNull Player player, @NotNull String teamId) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(teamId, "teamId cannot be null");

        String cleanId = teamId.trim().toLowerCase();
        UUID playerUuid = player.getUniqueId();

        if (hasTeam(playerUuid)) {
            plugin.getMessageManager().sendMessage(player, "team-already-in-team");
            return CompletableFuture.completedFuture(false);
        }

        Map<String, TeamInvite> targetInvites = pendingInvites.get(playerUuid);
        if (targetInvites == null) {
            plugin.getMessageManager().sendMessage(player, "team-invite-not-found");
            return CompletableFuture.completedFuture(false);
        }

        TeamInvite invite = targetInvites.remove(cleanId);
        if (invite == null) {
            plugin.getMessageManager().sendMessage(player, "team-invite-not-found");
            return CompletableFuture.completedFuture(false);
        }

        if (invite.isExpired()) {
            plugin.getMessageManager().sendMessage(player, "team-invite-expired");
            return CompletableFuture.completedFuture(false);
        }

        DanaTeam team = teams.get(cleanId);
        if (team == null) {
            plugin.getMessageManager().sendMessage(player, "team-not-found");
            return CompletableFuture.completedFuture(false);
        }

        TeamMember member = new TeamMember(playerUuid, player.getName(), TeamRole.MEMBER, Instant.now());
        return database.addMember(cleanId, member).thenApply(v -> {
            team.addMember(member);
            playerTeamMap.put(playerUuid, cleanId);

            applyTeamPermissions(player, cleanId, team.getColor());

            // Notify team members
            for (UUID memUuid : team.getMembers().keySet()) {
                Player p = Bukkit.getPlayer(memUuid);
                if (p != null && p.isOnline()) {
                    plugin.getMessageManager().sendMessage(
                        p,
                        "team-invite-accepted",
                        Placeholder.parsed("player", player.getName())
                    );
                }
            }

            return true;
        });
    }

    /**
     * Declines a pending invitation.
     *
     * @param playerUuid target player UUID
     * @param teamId     team identifier
     * @return true if an invite was found and removed
     */
    public boolean declineInvite(@NotNull UUID playerUuid, @NotNull String teamId) {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(teamId, "teamId cannot be null");

        Map<String, TeamInvite> targetInvites = pendingInvites.get(playerUuid);
        if (targetInvites == null) {
            return false;
        }

        TeamInvite invite = targetInvites.remove(teamId.trim().toLowerCase());
        if (invite != null) {
            Player target = Bukkit.getPlayer(playerUuid);
            DanaTeam team = teams.get(teamId.trim().toLowerCase());
            if (team != null && target != null) {
                Player inviter = Bukkit.getPlayer(invite.inviterUuid());
                if (inviter != null && inviter.isOnline()) {
                    plugin.getMessageManager().sendMessage(
                        inviter,
                        "team-invite-declined",
                        Placeholder.parsed("player", target.getName())
                    );
                }
            }
            return true;
        }
        return false;
    }

    public @NotNull List<TeamInvite> getPendingInvites(@NotNull UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Map<String, TeamInvite> targetInvites = pendingInvites.get(playerUuid);
        if (targetInvites == null || targetInvites.isEmpty()) {
            return List.of();
        }

        // Clean expired
        targetInvites.entrySet().removeIf(entry -> entry.getValue().isExpired());
        return new ArrayList<>(targetInvites.values());
    }

    /**
     * Kicks a member from a team.
     *
     * @param teamId     team identifier
     * @param kickerUuid UUID of the member initiating kick (must be LEADER)
     * @param targetUuid UUID of the member to kick
     * @return CompletableFuture completing when removed
     */
    public CompletableFuture<Void> kickMember(@NotNull String teamId, @NotNull UUID kickerUuid, @NotNull UUID targetUuid) {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(kickerUuid, "kickerUuid cannot be null");
        Objects.requireNonNull(targetUuid, "targetUuid cannot be null");

        DanaTeam team = teams.get(teamId.trim().toLowerCase());
        if (team == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (!team.isLeader(kickerUuid)) {
            throw new IllegalStateException("Only the team leader can kick members");
        }

        if (kickerUuid.equals(targetUuid)) {
            throw new IllegalArgumentException("Leader cannot kick themselves");
        }

        if (!team.hasMember(targetUuid)) {
            return CompletableFuture.completedFuture(null);
        }

        return database.removeMember(targetUuid).thenRun(() -> {
            team.removeMember(targetUuid);
            playerTeamMap.remove(targetUuid);

            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null && target.isOnline()) {
                removeTeamPermissions(target);
                plugin.getMessageManager().sendMessage(
                    target,
                    "team-kicked",
                    Placeholder.parsed("team", team.getFormattedName())
                );
            }

            for (UUID memUuid : team.getMembers().keySet()) {
                Player p = Bukkit.getPlayer(memUuid);
                if (p != null && p.isOnline()) {
                    plugin.getMessageManager().sendMessage(
                        p,
                        "team-member-kicked",
                        Placeholder.parsed("player", target != null ? target.getName() : "Un membre")
                    );
                }
            }
        });
    }

    /**
     * Handles player leaving their team.
     * If the leader leaves, the team is dissolved.
     *
     * @param player player leaving
     * @return CompletableFuture completing when left
     */
    public CompletableFuture<Void> leaveTeam(@NotNull Player player) {
        Objects.requireNonNull(player, "player cannot be null");
        UUID playerUuid = player.getUniqueId();

        DanaTeam team = getPlayerTeam(playerUuid).orElse(null);
        if (team == null) {
            plugin.getMessageManager().sendMessage(player, "team-not-in-team");
            return CompletableFuture.completedFuture(null);
        }

        if (team.isLeader(playerUuid)) {
            // Per specification: if leader leaves, team is disbanded
            return disbandTeam(team.getId()).thenRun(() -> {
                plugin.getMessageManager().sendMessage(player, "team-leader-left-disbanded");
            });
        }

        return database.removeMember(playerUuid).thenRun(() -> {
            team.removeMember(playerUuid);
            playerTeamMap.remove(playerUuid);
            removeTeamPermissions(player);

            plugin.getMessageManager().sendMessage(
                player,
                "team-left",
                Placeholder.parsed("team", team.getFormattedName())
            );

            for (UUID memUuid : team.getMembers().keySet()) {
                Player p = Bukkit.getPlayer(memUuid);
                if (p != null && p.isOnline()) {
                    plugin.getMessageManager().sendMessage(
                        p,
                        "team-member-left",
                        Placeholder.parsed("player", player.getName())
                    );
                }
            }
        });
    }

    /**
     * Attaches dynamic team permissions to the player.
     *
     * @param player target player
     * @param teamId team identifier
     * @param color  team color
     */
    public void applyTeamPermissions(@NotNull Player player, @NotNull String teamId, @NotNull TeamColor color) {
        removeTeamPermissions(player);

        PermissionAttachment attachment = player.addAttachment(plugin);
        attachment.setPermission("danaevent.team." + teamId.toLowerCase(), true);
        attachment.setPermission("danaevent.teamcolor." + color.getId().toLowerCase(), true);

        playerAttachments.put(player.getUniqueId(), attachment);
    }

    /**
     * Removes any dynamic team permissions for the player.
     *
     * @param player target player
     */
    public void removeTeamPermissions(@NotNull Player player) {
        PermissionAttachment attachment = playerAttachments.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /**
     * Synchronizes dynamic permissions when a player joins.
     *
     * @param player connecting player
     */
    public void handlePlayerJoin(@NotNull Player player) {
        DanaTeam team = getPlayerTeam(player.getUniqueId()).orElse(null);
        if (team != null) {
            applyTeamPermissions(player, team.getId(), team.getColor());
        }
    }

    /**
     * Cleans up permissions attachment when a player quits.
     *
     * @param player disconnecting player
     */
    public void handlePlayerQuit(@NotNull Player player) {
        removeTeamPermissions(player);
    }

    /**
     * Cleans up all team attachments, collections and listeners.
     */
    public void cleanUp() {
        for (UUID uuid : new ArrayList<>(playerAttachments.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                removeTeamPermissions(player);
            }
        }
        playerAttachments.clear();
        teams.clear();
        playerTeamMap.clear();
        colorTeamMap.clear();
        pendingInvites.clear();
    }
}
