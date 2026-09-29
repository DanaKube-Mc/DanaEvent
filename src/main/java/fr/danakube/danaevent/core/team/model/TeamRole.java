package fr.danakube.danaevent.core.team.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

/**
 * Roles a member can hold within a team.
 */
public enum TeamRole {
    LEADER("Chef d'équipe", true),
    MEMBER("Membre", false);

    private final String title;
    private final boolean isLeader;

    TeamRole(String title, boolean isLeader) {
        this.title = title;
        this.isLeader = isLeader;
    }

    public @NotNull String getTitle() {
        return title;
    }

    public boolean isLeader() {
        return isLeader;
    }

    public static @NotNull Optional<TeamRole> fromString(@Nullable String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String clean = input.trim().toUpperCase();
        return Arrays.stream(values())
            .filter(r -> r.name().equals(clean))
            .findFirst();
    }
}
