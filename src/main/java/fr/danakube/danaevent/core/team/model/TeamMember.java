package fr.danakube.danaevent.core.team.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a player membership within a team.
 */
public record TeamMember(
    @NotNull UUID playerUuid,
    @NotNull String lastKnownName,
    @NotNull TeamRole role,
    @NotNull Instant joinedAt
) {
    public TeamMember {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(lastKnownName, "lastKnownName cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(joinedAt, "joinedAt cannot be null");
    }

    public TeamMember(@NotNull UUID playerUuid, @NotNull String lastKnownName) {
        this(playerUuid, lastKnownName, TeamRole.MEMBER, Instant.now());
    }

    public TeamMember(@NotNull UUID playerUuid, @NotNull String lastKnownName, @NotNull TeamRole role) {
        this(playerUuid, lastKnownName, role, Instant.now());
    }

    public @NotNull TeamMember withRole(@NotNull TeamRole newRole) {
        return new TeamMember(this.playerUuid, this.lastKnownName, newRole, this.joinedAt);
    }

    public @NotNull TeamMember withName(@NotNull String newName) {
        return new TeamMember(this.playerUuid, newName, this.role, this.joinedAt);
    }

    public boolean isLeader() {
        return role == TeamRole.LEADER;
    }
}
