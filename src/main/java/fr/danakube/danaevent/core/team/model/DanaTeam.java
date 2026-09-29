package fr.danakube.danaevent.core.team.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain entity representing an event team.
 */
public class DanaTeam {

    private final String id;
    private String displayName;
    private TeamColor color;
    private UUID leaderUuid;
    private final Map<UUID, TeamMember> members = new ConcurrentHashMap<>();
    private final Instant createdAt;

    public DanaTeam(
        @NotNull String id,
        @NotNull String displayName,
        @NotNull TeamColor color,
        @NotNull UUID leaderUuid,
        @Nullable Collection<TeamMember> initialMembers,
        @NotNull Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null").trim().toLowerCase();
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null").trim();
        this.color = Objects.requireNonNull(color, "color cannot be null");
        this.leaderUuid = Objects.requireNonNull(leaderUuid, "leaderUuid cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");

        if (initialMembers != null) {
            for (TeamMember m : initialMembers) {
                this.members.put(m.playerUuid(), m);
            }
        }
    }

    public DanaTeam(@NotNull String id, @NotNull String displayName, @NotNull TeamColor color, @NotNull UUID leaderUuid) {
        this(id, displayName, color, leaderUuid, null, Instant.now());
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(@NotNull String displayName) {
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null").trim();
    }

    public @NotNull TeamColor getColor() {
        return color;
    }

    public void setColor(@NotNull TeamColor color) {
        this.color = Objects.requireNonNull(color, "color cannot be null");
    }

    public @NotNull UUID getLeaderUuid() {
        return leaderUuid;
    }

    public void setLeader(@NotNull UUID newLeaderUuid) {
        Objects.requireNonNull(newLeaderUuid, "newLeaderUuid cannot be null");
        if (!members.containsKey(newLeaderUuid)) {
            throw new IllegalArgumentException("New leader must already be a member of the team: " + newLeaderUuid);
        }
        // Demote existing leader if present in members
        TeamMember currentLeader = members.get(this.leaderUuid);
        if (currentLeader != null) {
            members.put(this.leaderUuid, currentLeader.withRole(TeamRole.MEMBER));
        }

        // Promote new leader
        TeamMember newLeader = members.get(newLeaderUuid);
        members.put(newLeaderUuid, newLeader.withRole(TeamRole.LEADER));
        this.leaderUuid = newLeaderUuid;
    }

    public @NotNull Instant getCreatedAt() {
        return createdAt;
    }

    public @NotNull Map<UUID, TeamMember> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    public int getMemberCount() {
        return members.size();
    }

    public boolean hasMember(@Nullable UUID uuid) {
        return uuid != null && members.containsKey(uuid);
    }

    public boolean isLeader(@Nullable UUID uuid) {
        return uuid != null && leaderUuid.equals(uuid);
    }

    public @NotNull Optional<TeamMember> getMember(@Nullable UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(members.get(uuid));
    }

    public void addMember(@NotNull TeamMember member) {
        Objects.requireNonNull(member, "member cannot be null");
        members.put(member.playerUuid(), member);
    }

    public @Nullable TeamMember removeMember(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return members.remove(uuid);
    }

    /**
     * Formats the team display name wrapped in its corresponding MiniMessage color.
     *
     * @return MiniMessage formatted team name string
     */
    public @NotNull String getFormattedName() {
        String tag = color.getId();
        return "<" + tag + ">" + displayName + "</" + tag + ">";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DanaTeam danaTeam)) return false;
        return Objects.equals(id, danaTeam.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "DanaTeam{" +
            "id='" + id + '\'' +
            ", displayName='" + displayName + '\'' +
            ", color=" + color +
            ", leaderUuid=" + leaderUuid +
            ", memberCount=" + members.size() +
            '}';
    }
}
