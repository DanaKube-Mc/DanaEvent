package fr.danakube.danaevent.core.team.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.team.manager.TeamManager;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import fr.danakube.danaevent.core.team.model.TeamMember;
import fr.danakube.danaevent.core.team.model.TeamRole;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TeamFriendlyFireListenerTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private TeamManager teamManager;
    private TeamFriendlyFireListener listener;
    private WorldMock world;

    private PlayerMock teammate1;
    private PlayerMock teammate2;
    private PlayerMock enemy;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        teamManager = plugin.getTeamManager();
        listener = plugin.getTeamFriendlyFireListener();
        world = server.addSimpleWorld("test_arena");

        teammate1 = server.addPlayer("BlueLeader");
        teammate2 = server.addPlayer("BlueSoldier");
        enemy = server.addPlayer("RedRival");

        // Create blue team with teammate1 and teammate2
        DanaTeam blueTeam = new DanaTeam("blue_team", "Blue Team", TeamColor.BLUE, teammate1.getUniqueId());
        blueTeam.addMember(new TeamMember(teammate1.getUniqueId(), teammate1.getName(), TeamRole.LEADER, Instant.now()));
        blueTeam.addMember(new TeamMember(teammate2.getUniqueId(), teammate2.getName(), TeamRole.MEMBER, Instant.now()));

        // Create red team with enemy
        DanaTeam redTeam = new DanaTeam("red_team", "Red Team", TeamColor.RED, enemy.getUniqueId());
        redTeam.addMember(new TeamMember(enemy.getUniqueId(), enemy.getName(), TeamRole.LEADER, Instant.now()));

        // Register in team manager database and memory
        teamManager.getDatabase().insertTeam(blueTeam).join();
        teamManager.getDatabase().insertTeam(redTeam).join();
        teamManager.loadAllTeams().join();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should cancel direct melee damage between teammates and notify attacker")
    void shouldCancelDirectMeleeDamageBetweenTeammates() {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(
            teammate1,
            teammate2,
            DamageCause.ENTITY_ATTACK,
            7.0
        );

        listener.onEntityDamageByEntity(event);

        assertThat(event.isCancelled()).isTrue();
        String message = teammate1.nextMessage();
        assertThat(message).contains("Vous ne pouvez pas blesser votre coéquipier");
    }

    @Test
    @DisplayName("Should NOT cancel melee damage between opponents in different teams")
    void shouldNotCancelMeleeDamageBetweenEnemies() {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(
            teammate1,
            enemy,
            DamageCause.ENTITY_ATTACK,
            7.0
        );

        listener.onEntityDamageByEntity(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Should NOT cancel self-inflicted damage")
    void shouldNotCancelSelfInflictedDamage() {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(
            teammate1,
            teammate1,
            DamageCause.ENTITY_ATTACK,
            4.0
        );

        listener.onEntityDamageByEntity(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Should cancel projectile arrow damage shot by a teammate")
    void shouldCancelTeammateArrowDamage() {
        Arrow arrow = world.spawn(new Location(world, 0, 64, 0), Arrow.class);
        arrow.setShooter(teammate1);

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(
            arrow,
            teammate2,
            DamageCause.PROJECTILE,
            9.0
        );

        listener.onEntityDamageByEntity(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Should NOT cancel projectile arrow damage shot by an enemy")
    void shouldNotCancelEnemyArrowDamage() {
        Arrow arrow = world.spawn(new Location(world, 0, 64, 0), Arrow.class);
        arrow.setShooter(enemy);

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(
            arrow,
            teammate2,
            DamageCause.PROJECTILE,
            9.0
        );

        listener.onEntityDamageByEntity(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Should zero out harmful splash potion intensity for teammates while affecting enemies")
    void shouldZeroOutHarmfulPotionIntensityForTeammates() {
        ThrownPotion potion = world.spawn(new Location(world, 0, 64, 0), ThrownPotion.class);
        potion.setShooter(teammate1);

        ItemStack potionItem = potion.getItem();
        PotionMeta meta = (PotionMeta) potionItem.getItemMeta();
        meta.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 100, 1), true);
        potion.setItem(potionItem);

        Map<LivingEntity, Double> affected = new HashMap<>();
        affected.put(teammate2, 1.0);
        affected.put(enemy, 1.0);

        PotionSplashEvent event = new PotionSplashEvent(potion, affected);
        listener.onPotionSplash(event);

        assertThat(event.getIntensity(teammate2)).isEqualTo(0.0);
        assertThat(event.getIntensity(enemy)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should NOT cancel beneficial splash potion effects on teammates")
    void shouldNotCancelBeneficialSplashPotionEffects() {
        ThrownPotion potion = world.spawn(new Location(world, 0, 64, 0), ThrownPotion.class);
        potion.setShooter(teammate1);

        ItemStack potionItem = potion.getItem();
        PotionMeta meta = (PotionMeta) potionItem.getItemMeta();
        meta.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1), true);
        potion.setItem(potionItem);

        Map<LivingEntity, Double> affected = new HashMap<>();
        affected.put(teammate2, 1.0);

        PotionSplashEvent event = new PotionSplashEvent(potion, affected);
        listener.onPotionSplash(event);

        assertThat(event.getIntensity(teammate2)).isEqualTo(1.0);
    }
}
