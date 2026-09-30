package fr.danakube.danaevent.modules.deacoudre.listener;

import fr.danakube.danaevent.modules.deacoudre.manager.DacJumpCallback;
import fr.danakube.danaevent.modules.deacoudre.manager.DacPoolManager;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacPlayerSession;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Handles fall damage cancellation and landing detection for Dé à Coudre jumpers.
 */
public class DacFallListener implements Listener {

    private final DacPoolManager poolManager;
    private final Function<UUID, Optional<DacPlayerSession>> sessionProvider;
    private final Function<UUID, Optional<DacArena>> arenaProvider;
    private final DacJumpCallback jumpCallback;

    public DacFallListener(
        @NotNull DacPoolManager poolManager,
        @NotNull Function<UUID, Optional<DacPlayerSession>> sessionProvider,
        @NotNull Function<UUID, Optional<DacArena>> arenaProvider,
        @NotNull DacJumpCallback jumpCallback
    ) {
        this.poolManager = Objects.requireNonNull(poolManager, "poolManager cannot be null");
        this.sessionProvider = Objects.requireNonNull(sessionProvider, "sessionProvider cannot be null");
        this.arenaProvider = Objects.requireNonNull(arenaProvider, "arenaProvider cannot be null");
        this.jumpCallback = Objects.requireNonNull(jumpCallback, "jumpCallback cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player player) {
            if (sessionProvider.apply(player.getUniqueId()).isPresent()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Player player = event.getPlayer();
        Optional<DacPlayerSession> sessionOpt = sessionProvider.apply(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }

        DacPlayerSession session = sessionOpt.get();
        if (!session.isJumping()) {
            return;
        }

        Optional<DacArena> arenaOpt = arenaProvider.apply(player.getUniqueId());
        if (arenaOpt.isEmpty()) {
            return;
        }

        DacArena arena = arenaOpt.get();
        if (arena.getPoolRegion() == null) {
            return;
        }

        // Only check landing when player has descended into the pool altitude range
        int poolMaxY = arena.getPoolRegion().getMaxY();
        if (to.getY() > poolMaxY + 1.5) {
            return;
        }

        World world = to.getWorld();
        if (world == null) {
            return;
        }

        Block blockAt = to.getBlock();
        Block blockFeet = to.clone().subtract(0, 0.2, 0).getBlock();

        // 1. Success: Landing in water within pool
        if (poolManager.isInsidePool(to, arena) && (poolManager.isWater(blockAt) || poolManager.isWater(blockFeet))) {
            Block targetWater = poolManager.isWater(blockAt) ? blockAt : blockFeet;
            boolean isPerfect = poolManager.isPerfectDac(targetWater);

            poolManager.convertToWool(targetWater, session.getColor());

            try {
                world.playSound(to, Sound.ENTITY_GENERIC_SPLASH, 1.0f, 1.2f);
                world.spawnParticle(Particle.SPLASH, to.clone().add(0, 0.5, 0), 25);
            } catch (Throwable ignored) {
            }

            jumpCallback.onJumpSuccess(player, targetWater, isPerfect);
            return;
        }

        // 2. Failure: Landing on wool or ground inside/around the pool
        boolean hitSolid = player.isOnGround()
            || poolManager.isWool(blockAt)
            || poolManager.isWool(blockFeet)
            || blockFeet.getType().isSolid();

        if (hitSolid) {
            try {
                world.playSound(to, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.8f);
                world.spawnParticle(Particle.DUST, to.clone().add(0, 0.5, 0), 20, new Particle.DustOptions(Color.RED, 1.5f));
            } catch (Throwable ignored) {
            }

            jumpCallback.onJumpFail(player, "Hit ground or wool");
        }
    }
}
