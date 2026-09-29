package fr.danakube.danaevent.core.team.listener;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.team.manager.TeamManager;
import fr.danakube.danaevent.core.team.manager.TeamVisualManager;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Handles visual events for teams during games and events.
 */
public class TeamVisualListener implements Listener {

    private final DanaEventPlugin plugin;
    private final TeamManager teamManager;
    private final TeamVisualManager visualManager;

    public TeamVisualListener(
        @NotNull DanaEventPlugin plugin,
        @NotNull TeamManager teamManager,
        @NotNull TeamVisualManager visualManager
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.teamManager = Objects.requireNonNull(teamManager, "teamManager cannot be null");
        this.visualManager = Objects.requireNonNull(visualManager, "visualManager cannot be null");
    }

    public @NotNull TeamVisualManager getVisualManager() {
        return visualManager;
    }

    public @NotNull TeamManager getTeamManager() {
        return teamManager;
    }
}
