package fr.danakube.danaevent.core.team.listener;

import fr.danakube.danaevent.core.team.manager.TeamManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Listens for player connection and disconnection events to manage dynamic team permissions.
 */
public class TeamConnectionListener implements Listener {

    private final TeamManager teamManager;

    public TeamConnectionListener(@NotNull TeamManager teamManager) {
        this.teamManager = Objects.requireNonNull(teamManager, "teamManager cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        teamManager.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        teamManager.handlePlayerQuit(event.getPlayer());
    }
}
