package fr.danakube.danaevent.modules.chromaticsheep.listener;

import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.chromaticsheep.manager.ArenaManager;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepArena;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Ensures players and sheep cannot exit the defined arena cuboid bounds.
 */
public class ArenaBoundaryListener implements Listener {

    private final ArenaManager arenaManager;
    private final Function<UUID, Optional<SheepArena>> playerArenaProvider;

    public ArenaBoundaryListener(
        @NotNull ArenaManager arenaManager,
        @NotNull Function<UUID, Optional<SheepArena>> playerArenaProvider
    ) {
        this.arenaManager = Objects.requireNonNull(arenaManager, "arenaManager cannot be null");
        this.playerArenaProvider = Objects.requireNonNull(playerArenaProvider, "playerArenaProvider cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Player player = event.getPlayer();
        Optional<SheepArena> arenaOpt = playerArenaProvider.apply(player.getUniqueId());
        if (arenaOpt.isEmpty()) {
            return;
        }

        SheepArena arena = arenaOpt.get();
        CuboidRegion bounds = arena.getBounds();
        if (bounds == null) {
            return;
        }

        // If player moves out of bounds
        if (!bounds.contains(to)) {
            Location from = event.getFrom();
            if (bounds.contains(from)) {
                // Cancel movement or reset to from
                event.setTo(from);
            } else {
                // Teleport to safe center
                World world = to.getWorld();
                if (world != null) {
                    Location center = bounds.getCenter(world);
                    event.setTo(center);
                }
            }
        }
    }

    /**
     * Confines a sheep to its arena bounds if it wandered outside.
     */
    public boolean checkAndConfineSheep(@NotNull Sheep sheep) {
        Optional<String> arenaIdOpt = SheepData.getArenaId(sheep);
        if (arenaIdOpt.isEmpty()) {
            return false;
        }

        Optional<SheepArena> arenaOpt = arenaManager.getArena(arenaIdOpt.get());
        if (arenaOpt.isEmpty()) {
            return false;
        }

        SheepArena arena = arenaOpt.get();
        CuboidRegion bounds = arena.getBounds();
        if (bounds == null) {
            return false;
        }

        Location loc = sheep.getLocation();
        if (!bounds.contains(loc)) {
            World world = loc.getWorld();
            if (world != null) {
                Location center = bounds.getCenter(world);
                Vector direction = center.toVector().subtract(loc.toVector()).normalize().multiply(0.5);
                sheep.setVelocity(direction);
                sheep.teleport(center);
                return true;
            }
        }
        return false;
    }
}
