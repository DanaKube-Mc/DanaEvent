package fr.danakube.danaevent.modules.chromaticsheep.listener;

import fr.danakube.danaevent.modules.chromaticsheep.item.PaintBombItem;
import fr.danakube.danaevent.modules.chromaticsheep.manager.HerdManager;
import fr.danakube.danaevent.modules.chromaticsheep.manager.SheepScoreManager;
import fr.danakube.danaevent.modules.chromaticsheep.model.PlayerSheepSession;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Handles launching PaintBombs and resolving AoE sheep dyeing upon impact.
 */
public class PaintBombListener implements Listener {

    public static final NamespacedKey KEY_BOMB_PROJECTILE = new NamespacedKey("danaevent", "mc_thrown_bomb");
    public static final NamespacedKey KEY_BOMB_SHOOTER = new NamespacedKey("danaevent", "mc_bomb_shooter");
    public static final NamespacedKey KEY_BOMB_ARENA = new NamespacedKey("danaevent", "mc_bomb_arena");

    public static final double BOMB_RADIUS = 3.5;

    private final HerdManager herdManager;
    private final SheepScoreManager scoreManager;
    private final Function<UUID, Optional<PlayerSheepSession>> sessionProvider;

    public PaintBombListener(
        @NotNull HerdManager herdManager,
        @NotNull SheepScoreManager scoreManager,
        @NotNull Function<UUID, Optional<PlayerSheepSession>> sessionProvider
    ) {
        this.herdManager = Objects.requireNonNull(herdManager, "herdManager cannot be null");
        this.scoreManager = Objects.requireNonNull(scoreManager, "scoreManager cannot be null");
        this.sessionProvider = Objects.requireNonNull(sessionProvider, "sessionProvider cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerThrowBomb(@NotNull PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (!PaintBombItem.isPaintBomb(item)) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        Optional<PlayerSheepSession> sessionOpt = sessionProvider.apply(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }

        PlayerSheepSession session = sessionOpt.get();

        if (!session.canThrowBomb()) {
            double remaining = session.getRemainingBombCooldownSeconds();
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<red>Bombe en recharge ! Encore <yellow>" + remaining + "s</yellow>.</red>"
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            return;
        }

        // Launch snowball projectile
        Snowball snowball = player.launchProjectile(Snowball.class);
        PersistentDataContainer pdc = snowball.getPersistentDataContainer();
        pdc.set(KEY_BOMB_PROJECTILE, PersistentDataType.BYTE, (byte) 1);
        pdc.set(KEY_BOMB_SHOOTER, PersistentDataType.STRING, player.getUniqueId().toString());
        pdc.set(KEY_BOMB_ARENA, PersistentDataType.STRING, session.getArenaId());

        session.recordBombThrow();
        player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.9f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBombHit(@NotNull ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        PersistentDataContainer pdc = projectile.getPersistentDataContainer();
        if (!pdc.has(KEY_BOMB_PROJECTILE, PersistentDataType.BYTE)) {
            return;
        }

        String arenaId = pdc.get(KEY_BOMB_ARENA, PersistentDataType.STRING);
        String shooterRaw = pdc.get(KEY_BOMB_SHOOTER, PersistentDataType.STRING);
        if (arenaId == null || shooterRaw == null) {
            return;
        }

        UUID shooterUuid;
        try {
            shooterUuid = UUID.fromString(shooterRaw);
        } catch (IllegalArgumentException e) {
            return;
        }

        Optional<PlayerSheepSession> sessionOpt = sessionProvider.apply(shooterUuid);
        if (sessionOpt.isEmpty()) {
            return;
        }

        PlayerSheepSession session = sessionOpt.get();
        Location hitLoc = getHitLocation(event, projectile);

        // Find and dye sheep in radius
        List<Sheep> sheepInRadius = herdManager.getNearbyGameSheep(hitLoc, BOMB_RADIUS, arenaId);
        int affectedCount = 0;

        for (Sheep sheep : sheepInRadius) {
            if (!SheepData.isProtected(sheep)) {
                sheep.setColor(session.getColor());
                SheepData.setProtectedUntil(sheep, System.currentTimeMillis() + 1500L);
                SheepData.setLastDyed(sheep, session.getEffectiveHolderUuid());
                affectedCount++;
            }
        }

        // Play explosion particles and sound
        playExplosionFeedback(hitLoc, session.getColor());

        // Award points
        if (affectedCount > 0) {
            int newScore = scoreManager.handleBombImpact(arenaId, session.getEffectiveHolderUuid(), affectedCount);
            session.setScorePoints(newScore);
        }
    }

    private Location getHitLocation(ProjectileHitEvent event, Projectile projectile) {
        if (event.getHitEntity() != null) {
            return event.getHitEntity().getLocation();
        } else if (event.getHitBlock() != null) {
            return event.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
        }
        return projectile.getLocation();
    }

    private void playExplosionFeedback(Location loc, DyeColor color) {
        if (loc.getWorld() != null) {
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
            try {
                Color bukkitColor = color.getColor();
                Particle.DustOptions dust = new Particle.DustOptions(bukkitColor, 2.5f);
                loc.getWorld().spawnParticle(Particle.DUST, loc, 30, 1.5, 1.0, 1.5, dust);
            } catch (Throwable ignored) {
                // MockBukkit particle fallback
            }
        }
    }
}
