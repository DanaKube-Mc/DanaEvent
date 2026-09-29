package fr.danakube.danaevent.core.player;

import fr.danakube.danaevent.DanaEventPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Listens for player connection and disconnection events to perform crash recovery
 * and ensure persistent player states.
 */
public class PlayerCrashRecoveryListener implements Listener {

    private final DanaEventPlugin plugin;
    private final PlayerStateManager playerStateManager;

    public PlayerCrashRecoveryListener(DanaEventPlugin plugin, PlayerStateManager playerStateManager) {
        this.plugin = Objects.requireNonNull(plugin, "DanaEventPlugin cannot be null");
        this.playerStateManager = Objects.requireNonNull(playerStateManager, "PlayerStateManager cannot be null");
    }

    /**
     * When a player joins, checks if they have an orphaned snapshot (e.g. from an unexpected server crash
     * or disconnection during an event). If found, restores their state, teleports them to safety,
     * informs them via MessageManager, and deletes the orphaned snapshot.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (playerStateManager.hasSnapshot(player.getUniqueId())) {
            boolean restored = playerStateManager.restore(player, true);
            if (restored) {
                plugin.getMessageManager().sendMessage(player, "crash-recovery-restored");
                plugin.getLogger().info("Successfully recovered player state for " + player.getName()
                    + " (" + player.getUniqueId() + ") after unexpected interruption.");
            }
        }
    }

    /**
     * When a player quits, ensures any active snapshot remains safely stored in the database.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        playerStateManager.getSnapshot(player.getUniqueId()).ifPresent(snapshot -> {
            try {
                plugin.getDatabaseManager().saveSnapshot(player.getUniqueId(), snapshot.toByteArray());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to persist snapshot in database on quit for "
                    + player.getName(), e);
            }
        });
    }
}
