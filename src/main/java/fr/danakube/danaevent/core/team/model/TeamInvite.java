package fr.danakube.danaevent.core.team.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a pending invitation to join a team.
 */
public record TeamInvite(
    @NotNull String teamId,
    @NotNull UUID inviterUuid,
    @NotNull UUID targetUuid,
    @NotNull Instant createdAt,
    @NotNull Instant expiresAt
) {
    public static final long DEFAULT_EXPIRY_SECONDS = 60L;

    public TeamInvite {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(inviterUuid, "inviterUuid cannot be null");
        Objects.requireNonNull(targetUuid, "targetUuid cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
    }

    public static @NotNull TeamInvite create(@NotNull String teamId, @NotNull UUID inviterUuid, @NotNull UUID targetUuid) {
        return create(teamId, inviterUuid, targetUuid, DEFAULT_EXPIRY_SECONDS);
    }

    public static @NotNull TeamInvite create(@NotNull String teamId, @NotNull UUID inviterUuid, @NotNull UUID targetUuid, long durationSeconds) {
        Instant now = Instant.now();
        return new TeamInvite(teamId, inviterUuid, targetUuid, now, now.plusSeconds(durationSeconds));
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public long getRemainingSeconds() {
        long remaining = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }
}
