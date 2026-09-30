package fr.danakube.danaevent.modules.deacoudre.listener;

import fr.danakube.danaevent.modules.deacoudre.manager.DacGameManager;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacGame;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameState;
import fr.danakube.danaevent.modules.deacoudre.model.DacPlayerSession;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Gatekeeper for the diving platform and pool, blocking inactive participants or spectators from jumping early.
 */
public class DacDivingPlatformListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final DacGameManager gameManager;

    public DacDivingPlatformListener(@NotNull DacGameManager gameManager) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        if (from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        Optional<DacPlayerSession> sessionOpt = gameManager.getSession(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }

        DacPlayerSession session = sessionOpt.get();
        Optional<DacArena> arenaOpt = gameManager.getPlayerArena(player.getUniqueId());
        if (arenaOpt.isEmpty()) {
            return;
        }

        DacArena arena = arenaOpt.get();
        DacGame game = gameManager.getGame(arena.getId());
        if (game == null || game.getState() != DacGameState.IN_GAME) {
            return;
        }

        // Active jumper is authorized to move and jump
        if (session.isJumping()) {
            return;
        }

        // Check if inactive player enters diving platform or falls into pool
        boolean enteringDiving = isNearDivingPlatform(to, arena);
        boolean insidePool = arena.getPoolRegion() != null && arena.getPoolRegion().contains(to);

        if (enteringDiving || insidePool) {
            if (arena.getLobbyLocation() != null) {
                event.setTo(arena.getLobbyLocation());
            } else {
                event.setCancelled(true);
            }
            player.sendMessage(MM.deserialize("<red>Attendez votre tour pour monter sur le plongeoir ou sauter !</red>"));
        }
    }

    private boolean isNearDivingPlatform(@NotNull Location loc, @NotNull DacArena arena) {
        Location diving = arena.getDivingLocation();
        if (diving == null || diving.getWorld() == null || loc.getWorld() == null) {
            return false;
        }
        if (!diving.getWorld().equals(loc.getWorld())) {
            return false;
        }
        return diving.distanceSquared(loc) <= 2.25; // within 1.5 blocks
    }
}
