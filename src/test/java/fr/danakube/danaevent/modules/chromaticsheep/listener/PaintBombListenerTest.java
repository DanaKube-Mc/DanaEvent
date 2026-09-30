package fr.danakube.danaevent.modules.chromaticsheep.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.chromaticsheep.item.PaintBombItem;
import fr.danakube.danaevent.modules.chromaticsheep.manager.HerdManager;
import fr.danakube.danaevent.modules.chromaticsheep.manager.SheepScoreManager;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameFormat;
import fr.danakube.danaevent.modules.chromaticsheep.model.PlayerSheepSession;
import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepArena;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepData;
import fr.danakube.danaevent.modules.chromaticsheep.model.SpecialSheepType;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Snowball;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class PaintBombListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private HerdManager herdManager;
    private SheepScoreManager scoreManager;
    private PaintBombListener listener;
    private final Map<UUID, PlayerSheepSession> activeSessions = new ConcurrentHashMap<>();

    private final String arenaId = "bomb_arena";
    private PlayerSheepSession session;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("bomb_world");
        player = server.addPlayer();

        herdManager = new HerdManager();
        scoreManager = new SheepScoreManager();
        listener = new PaintBombListener(herdManager, scoreManager, uuid -> Optional.ofNullable(activeSessions.get(uuid)));

        session = new PlayerSheepSession(player.getUniqueId(), null, DyeColor.MAGENTA, arenaId);
        activeSessions.put(player.getUniqueId(), session);
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should launch snowball and enforce cooldown on subsequent throw")
    void shouldLaunchSnowballAndEnforceCooldown() {
        ItemStack bombItem = PaintBombItem.createItem();
        player.getInventory().setItemInMainHand(bombItem);

        PlayerInteractEvent event1 = new PlayerInteractEvent(
            player,
            Action.RIGHT_CLICK_AIR,
            bombItem,
            null,
            null,
            EquipmentSlot.HAND
        );
        listener.onPlayerThrowBomb(event1);

        assertThat(event1.isCancelled()).isTrue();
        assertThat(session.canThrowBomb()).isFalse();

        // Attempt second throw while on cooldown
        PlayerInteractEvent event2 = new PlayerInteractEvent(
            player,
            Action.RIGHT_CLICK_AIR,
            bombItem,
            null,
            null,
            EquipmentSlot.HAND
        );
        listener.onPlayerThrowBomb(event2);

        // Player should receive warning message
        assertThat(player.nextComponentMessage()).isNotNull();
    }

    @Test
    @DisplayName("Projectile hit should dye all nearby sheep within 3.5 blocks and award bomb points")
    void shouldDyeSheepOnImpact() {
        SheepArena arena = new SheepArena(arenaId, "Bomb Arena", GameFormat.SOLO, ScoringMode.FINAL_COUNT);
        arena.setSheepCount(5);
        Location center = new Location(world, 10, 64, 10);
        arena.setBounds(new CuboidRegion(
            center.clone().add(-10, -5, -10),
            center.clone().add(10, 5, 10)
        ));

        // Spawn 2 sheep inside radius 3.5
        Sheep sheep1 = (Sheep) world.spawnEntity(center.clone().add(1, 0, 0), EntityType.SHEEP);
        SheepData.tagSheep(sheep1, arenaId, SpecialSheepType.NORMAL);
        sheep1.setColor(DyeColor.WHITE);
        herdManager.trackSheep(arenaId, sheep1.getUniqueId());

        Sheep sheep2 = (Sheep) world.spawnEntity(center.clone().add(0, 0, 2), EntityType.SHEEP);
        SheepData.tagSheep(sheep2, arenaId, SpecialSheepType.NORMAL);
        sheep2.setColor(DyeColor.WHITE);
        herdManager.trackSheep(arenaId, sheep2.getUniqueId());

        // Spawn snowball projectile and tag PDC
        Snowball snowball = (Snowball) world.spawnEntity(center, EntityType.SNOWBALL);
        snowball.getPersistentDataContainer().set(PaintBombListener.KEY_BOMB_PROJECTILE, PersistentDataType.BYTE, (byte) 1);
        snowball.getPersistentDataContainer().set(PaintBombListener.KEY_BOMB_SHOOTER, PersistentDataType.STRING, player.getUniqueId().toString());
        snowball.getPersistentDataContainer().set(PaintBombListener.KEY_BOMB_ARENA, PersistentDataType.STRING, arenaId);

        ProjectileHitEvent hitEvent = new ProjectileHitEvent(snowball);
        listener.onBombHit(hitEvent);

        // Verify that sheep in radius were colored MAGENTA
        Map<DyeColor, Integer> counts = herdManager.countColors(arenaId, world);
        assertThat(counts.get(DyeColor.MAGENTA)).isGreaterThan(0);
        assertThat(session.getScorePoints()).isGreaterThan(0);
    }
}
