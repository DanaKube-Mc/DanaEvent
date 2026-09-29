package fr.danakube.danaevent.core.team.listener;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.team.manager.TeamManager;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for damage, projectile, and potion events to cancel friendly fire between teammates.
 */
public class TeamFriendlyFireListener implements Listener {

    private static final long MESSAGE_COOLDOWN_MILLIS = 1500L;

    private static final Set<PotionEffectType> HARMFUL_EFFECTS = Set.of(
        PotionEffectType.INSTANT_DAMAGE,
        PotionEffectType.POISON,
        PotionEffectType.SLOWNESS,
        PotionEffectType.WEAKNESS,
        PotionEffectType.BLINDNESS,
        PotionEffectType.WITHER,
        PotionEffectType.HUNGER,
        PotionEffectType.NAUSEA,
        PotionEffectType.LEVITATION,
        PotionEffectType.DARKNESS,
        PotionEffectType.MINING_FATIGUE
    );

    private final DanaEventPlugin plugin;
    private final TeamManager teamManager;
    private final Map<UUID, Long> messageCooldowns = new ConcurrentHashMap<>();

    public TeamFriendlyFireListener(@NotNull DanaEventPlugin plugin, @NotNull TeamManager teamManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.teamManager = Objects.requireNonNull(teamManager, "teamManager cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        Optional<DanaTeam> victimTeam = teamManager.getPlayerTeam(victim.getUniqueId());
        Optional<DanaTeam> attackerTeam = teamManager.getPlayerTeam(attacker.getUniqueId());

        if (victimTeam.isPresent() && attackerTeam.isPresent()
                && victimTeam.get().getId().equalsIgnoreCase(attackerTeam.get().getId())) {
            event.setCancelled(true);
            notifyAttacker(attacker, victim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        if (!(potion.getShooter() instanceof Player thrower)) {
            return;
        }

        boolean hasHarmful = false;
        for (PotionEffect effect : potion.getEffects()) {
            if (HARMFUL_EFFECTS.contains(effect.getType())) {
                hasHarmful = true;
                break;
            }
        }

        if (!hasHarmful) {
            return;
        }

        Optional<DanaTeam> throwerTeam = teamManager.getPlayerTeam(thrower.getUniqueId());
        if (throwerTeam.isEmpty()) {
            return;
        }

        for (LivingEntity entity : event.getAffectedEntities()) {
            if (entity instanceof Player victim && !victim.getUniqueId().equals(thrower.getUniqueId())) {
                Optional<DanaTeam> victimTeam = teamManager.getPlayerTeam(victim.getUniqueId());
                if (victimTeam.isPresent() && victimTeam.get().getId().equalsIgnoreCase(throwerTeam.get().getId())) {
                    event.setIntensity(victim, 0.0);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        if (!(cloud.getSource() instanceof Player source)) {
            return;
        }

        boolean hasHarmful = false;
        for (PotionEffect effect : cloud.getCustomEffects()) {
            if (HARMFUL_EFFECTS.contains(effect.getType())) {
                hasHarmful = true;
                break;
            }
        }

        if (!hasHarmful) {
            return;
        }

        Optional<DanaTeam> sourceTeam = teamManager.getPlayerTeam(source.getUniqueId());
        if (sourceTeam.isEmpty()) {
            return;
        }

        event.getAffectedEntities().removeIf(entity -> {
            if (entity instanceof Player victim && !victim.getUniqueId().equals(source.getUniqueId())) {
                Optional<DanaTeam> victimTeam = teamManager.getPlayerTeam(victim.getUniqueId());
                return victimTeam.isPresent() && victimTeam.get().getId().equalsIgnoreCase(sourceTeam.get().getId());
            }
            return false;
        });
    }

    /**
     * Resolves the true player attacker from an entity or projectile.
     *
     * @param damager the damager entity
     * @return the Player responsible for the damage, or null
     */
    public @Nullable Player resolveAttacker(@Nullable Entity damager) {
        if (damager == null) {
            return null;
        }
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        if (damager instanceof AreaEffectCloud aec && aec.getSource() instanceof Player aecSource) {
            return aecSource;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player tntSource) {
            return tntSource;
        }
        if (damager instanceof EvokerFangs fangs && fangs.getOwner() instanceof Player fangsOwner) {
            return fangsOwner;
        }
        if (damager instanceof LightningStrike lightning && lightning.getCausingPlayer() != null) {
            return lightning.getCausingPlayer();
        }
        return null;
    }

    private void notifyAttacker(@NotNull Player attacker, @NotNull Player victim) {
        long now = System.currentTimeMillis();
        Long last = messageCooldowns.get(attacker.getUniqueId());
        if (last == null || (now - last) >= MESSAGE_COOLDOWN_MILLIS) {
            messageCooldowns.put(attacker.getUniqueId(), now);
            plugin.getMessageManager().sendMessage(
                attacker,
                "team-friendly-fire",
                Placeholder.parsed("player", victim.getName())
            );
        }
    }
}
