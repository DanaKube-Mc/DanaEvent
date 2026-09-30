package fr.danakube.danaevent.modules.chromaticsheep.listener;

import fr.danakube.danaevent.modules.chromaticsheep.item.PaintBrushItem;
import fr.danakube.danaevent.modules.chromaticsheep.manager.HerdManager;
import fr.danakube.danaevent.modules.chromaticsheep.manager.SheepScoreManager;
import fr.danakube.danaevent.modules.chromaticsheep.model.PlayerSheepSession;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepData;
import fr.danakube.danaevent.modules.chromaticsheep.model.SpecialSheepType;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Handles right-clicking sheep with the PaintBrush to dye them, trigger special events and update scores.
 */
public class SheepInteractListener implements Listener {

    public static final long DYE_PROTECTION_MILLIS = 1500L;

    private final HerdManager herdManager;
    private final SheepScoreManager scoreManager;
    private final Function<UUID, Optional<PlayerSheepSession>> sessionProvider;

    public SheepInteractListener(
        @NotNull HerdManager herdManager,
        @NotNull SheepScoreManager scoreManager,
        @NotNull Function<UUID, Optional<PlayerSheepSession>> sessionProvider
    ) {
        this.herdManager = Objects.requireNonNull(herdManager, "herdManager cannot be null");
        this.scoreManager = Objects.requireNonNull(scoreManager, "scoreManager cannot be null");
        this.sessionProvider = Objects.requireNonNull(sessionProvider, "sessionProvider cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractSheep(@NotNull PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Entity target = event.getRightClicked();
        if (!(target instanceof Sheep sheep) || !SheepData.isGameSheep(sheep)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!PaintBrushItem.isPaintBrush(item)) {
            return;
        }

        event.setCancelled(true);

        Optional<PlayerSheepSession> sessionOpt = sessionProvider.apply(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }

        PlayerSheepSession session = sessionOpt.get();

        // Distance check (max 4.0 blocks)
        if (player.getLocation().distanceSquared(sheep.getLocation()) > 16.0) {
            return;
        }

        // Check 1.5s protection
        if (SheepData.isProtected(sheep)) {
            return;
        }

        UUID holderUuid = session.getEffectiveHolderUuid();

        // Check if already dyed by this player/team
        Optional<UUID> lastDyedOpt = SheepData.getLastDyedBy(sheep);
        if (lastDyedOpt.isPresent() && lastDyedOpt.get().equals(holderUuid)) {
            return;
        }

        boolean wasNeutral = lastDyedOpt.isEmpty();
        boolean wasSteal = lastDyedOpt.isPresent();
        SpecialSheepType specialType = SheepData.getSpecialType(sheep);

        // Apply player's color
        sheep.setColor(session.getColor());
        SheepData.setProtectedUntil(sheep, System.currentTimeMillis() + DYE_PROTECTION_MILLIS);
        SheepData.setLastDyed(sheep, holderUuid);

        // Play sounds and particles
        playFeedback(player, sheep.getLocation(), session.getColor());

        // Handle special sheep effects
        handleSpecialEffects(player, sheep, specialType, session);

        // Award score
        int awarded = scoreManager.handleActionDye(session.getArenaId(), holderUuid, wasNeutral, wasSteal, specialType);
        session.setScorePoints(awarded);
    }

    private void handleSpecialEffects(
        Player player,
        Sheep sheep,
        SpecialSheepType specialType,
        PlayerSheepSession session
    ) {
        switch (specialType) {
            case GOLDEN -> {
                // Speed II for 5 seconds (100 ticks)
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
            }
            case RAINBOW -> {
                // Shockwave: converts nearby sheep within 5 blocks
                List<Sheep> nearby = herdManager.getNearbyGameSheep(sheep.getLocation(), 5.0, session.getArenaId());
                for (Sheep other : nearby) {
                    if (!other.getUniqueId().equals(sheep.getUniqueId())) {
                        other.setColor(session.getColor());
                        SheepData.setProtectedUntil(other, System.currentTimeMillis() + DYE_PROTECTION_MILLIS);
                        SheepData.setLastDyed(other, session.getEffectiveHolderUuid());
                        scoreManager.handleActionDye(session.getArenaId(), session.getEffectiveHolderUuid(), false, true, SheepData.getSpecialType(other));
                    }
                }
            }
            case TRICKSTER -> {
                // Blindness for 2 seconds (40 ticks)
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                Vector repulsion = player.getLocation().toVector().subtract(sheep.getLocation().toVector()).normalize().multiply(0.6).setY(0.3);
                player.setVelocity(repulsion);
            }
            case NORMAL -> {
                // Standard behavior
            }
        }
    }

    private void playFeedback(Player player, Location loc, DyeColor color) {
        player.playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.8f);

        Color bukkitColor = color.getColor();
        try {
            Particle.DustOptions dust = new Particle.DustOptions(bukkitColor, 1.5f);
            loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 0.8, 0), 12, 0.3, 0.3, 0.3, dust);
        } catch (Throwable ignored) {
            // MockBukkit particle fallback
        }
    }
}
