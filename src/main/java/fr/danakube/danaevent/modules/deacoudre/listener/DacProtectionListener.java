package fr.danakube.danaevent.modules.deacoudre.listener;

import fr.danakube.danaevent.modules.deacoudre.manager.DacPoolManager;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacPlayerSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Protects Dé à Coudre arenas from griefing and blocks participants from dropping items.
 */
public class DacProtectionListener implements Listener {

    private final DacPoolManager poolManager;
    private final Function<UUID, Optional<DacPlayerSession>> sessionProvider;
    private final Supplier<Collection<DacArena>> arenasSupplier;

    public DacProtectionListener(
        @NotNull DacPoolManager poolManager,
        @NotNull Function<UUID, Optional<DacPlayerSession>> sessionProvider,
        @NotNull Supplier<Collection<DacArena>> arenasSupplier
    ) {
        this.poolManager = Objects.requireNonNull(poolManager, "poolManager cannot be null");
        this.sessionProvider = Objects.requireNonNull(sessionProvider, "sessionProvider cannot be null");
        this.arenasSupplier = Objects.requireNonNull(arenasSupplier, "arenasSupplier cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (sessionProvider.apply(player.getUniqueId()).isPresent()) {
            event.setCancelled(true);
            return;
        }

        Location loc = event.getBlock().getLocation();
        for (DacArena arena : arenasSupplier.get()) {
            if (poolManager.isInsidePool(loc, arena)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (sessionProvider.apply(player.getUniqueId()).isPresent()) {
            event.setCancelled(true);
            return;
        }

        Location loc = event.getBlock().getLocation();
        for (DacArena arena : arenasSupplier.get()) {
            if (poolManager.isInsidePool(loc, arena)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (sessionProvider.apply(player.getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }
}
