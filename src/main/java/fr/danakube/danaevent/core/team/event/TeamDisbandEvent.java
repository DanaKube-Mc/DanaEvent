package fr.danakube.danaevent.core.team.event;

import fr.danakube.danaevent.core.team.model.DanaTeam;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired when an event team is disbanded.
 */
public class TeamDisbandEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final DanaTeam team;

    public TeamDisbandEvent(@NotNull DanaTeam team) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.team = Objects.requireNonNull(team, "team cannot be null");
    }

    public @NotNull DanaTeam getTeam() {
        return team;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
