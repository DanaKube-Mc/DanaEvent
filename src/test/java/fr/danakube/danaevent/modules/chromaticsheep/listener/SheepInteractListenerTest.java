package fr.danakube.danaevent.modules.chromaticsheep.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.chromaticsheep.item.PaintBrushItem;
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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class SheepInteractListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private HerdManager herdManager;
    private SheepScoreManager scoreManager;
    private SheepInteractListener listener;
    private final Map<UUID, PlayerSheepSession> activeSessions = new ConcurrentHashMap<>();

    private final String arenaId = "pasture_test";
    private PlayerSheepSession session;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("interact_world");
        player = server.addPlayer();

        herdManager = new HerdManager();
        scoreManager = new SheepScoreManager();
        listener = new SheepInteractListener(herdManager, scoreManager, uuid -> Optional.ofNullable(activeSessions.get(uuid)));

        session = new PlayerSheepSession(player.getUniqueId(), null, DyeColor.BLUE, arenaId);
        activeSessions.put(player.getUniqueId(), session);

        // Player holds paint brush
        player.getInventory().setItemInMainHand(PaintBrushItem.createItem());
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should dye neutral sheep, set protection, and award score")
    void shouldDyeNeutralSheep() {
        Sheep sheep = (Sheep) world.spawnEntity(player.getLocation().add(1, 0, 0), EntityType.SHEEP);
        SheepData.tagSheep(sheep, arenaId, SpecialSheepType.NORMAL);
        sheep.setColor(DyeColor.WHITE);

        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, sheep, EquipmentSlot.HAND);
        listener.onPlayerInteractSheep(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(sheep.getColor()).isEqualTo(DyeColor.BLUE);
        assertThat(SheepData.getLastDyedBy(sheep)).contains(player.getUniqueId());
        assertThat(SheepData.isProtected(sheep)).isTrue();
        assertThat(session.getScorePoints()).isEqualTo(1); // 1 pt for neutral dye
    }

    @Test
    @DisplayName("Should ignore clicking an already dyed sheep by the same holder")
    void shouldIgnoreRedundantDyeing() {
        Sheep sheep = (Sheep) world.spawnEntity(player.getLocation().add(1, 0, 0), EntityType.SHEEP);
        SheepData.tagSheep(sheep, arenaId, SpecialSheepType.NORMAL);
        sheep.setColor(DyeColor.BLUE);
        SheepData.setLastDyed(sheep, player.getUniqueId());

        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, sheep, EquipmentSlot.HAND);
        listener.onPlayerInteractSheep(event);

        // Score shouldn't change
        assertThat(session.getScorePoints()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should respect anti-spam protection countdown")
    void shouldRespectProtection() {
        Sheep sheep = (Sheep) world.spawnEntity(player.getLocation().add(1, 0, 0), EntityType.SHEEP);
        SheepData.tagSheep(sheep, arenaId, SpecialSheepType.NORMAL);
        sheep.setColor(DyeColor.RED);
        SheepData.setLastDyed(sheep, UUID.randomUUID());
        // Set protected for 10 seconds into future
        SheepData.setProtectedUntil(sheep, System.currentTimeMillis() + 10_000L);

        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, sheep, EquipmentSlot.HAND);
        listener.onPlayerInteractSheep(event);

        // Color should NOT change to blue
        assertThat(sheep.getColor()).isEqualTo(DyeColor.RED);
        assertThat(session.getScorePoints()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should apply Speed II and 5x score on GOLDEN sheep")
    void shouldHandleGoldenSheep() {
        Sheep goldenSheep = (Sheep) world.spawnEntity(player.getLocation().add(1, 0, 0), EntityType.SHEEP);
        herdManager.configureSheepType(goldenSheep, SpecialSheepType.GOLDEN, arenaId);

        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, goldenSheep, EquipmentSlot.HAND);
        listener.onPlayerInteractSheep(event);

        assertThat(goldenSheep.getColor()).isEqualTo(DyeColor.BLUE);
        assertThat(player.hasPotionEffect(PotionEffectType.SPEED)).isTrue();
        assertThat(session.getScorePoints()).isEqualTo(5); // 1 pt neutral * 5
    }

    @Test
    @DisplayName("Should trigger shockwave AoE dye on RAINBOW sheep")
    void shouldHandleRainbowSheep() {
        // Create rainbow sheep at player location
        Sheep rainbowSheep = (Sheep) world.spawnEntity(player.getLocation().add(1, 0, 0), EntityType.SHEEP);
        herdManager.configureSheepType(rainbowSheep, SpecialSheepType.RAINBOW, arenaId);
        herdManager.trackSheep(arenaId, rainbowSheep.getUniqueId());

        // Create nearby normal sheep 1 block away
        Sheep nearbySheep = (Sheep) world.spawnEntity(player.getLocation().add(2, 0, 0), EntityType.SHEEP);
        SheepData.tagSheep(nearbySheep, arenaId, SpecialSheepType.NORMAL);
        nearbySheep.setColor(DyeColor.WHITE);
        herdManager.trackSheep(arenaId, nearbySheep.getUniqueId());

        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, rainbowSheep, EquipmentSlot.HAND);
        listener.onPlayerInteractSheep(event);

        assertThat(rainbowSheep.getColor()).isEqualTo(DyeColor.BLUE);
        assertThat(nearbySheep.getColor()).isEqualTo(DyeColor.BLUE);

        Map<DyeColor, Integer> counts = herdManager.countColors(arenaId, world);
        assertThat(counts.get(DyeColor.BLUE)).isEqualTo(2);
    }

    @Test
    @DisplayName("Should apply Blindness and knockback on TRICKSTER sheep")
    void shouldHandleTricksterSheep() {
        Sheep tricksterSheep = (Sheep) world.spawnEntity(player.getLocation().add(1, 0, 0), EntityType.SHEEP);
        herdManager.configureSheepType(tricksterSheep, SpecialSheepType.TRICKSTER, arenaId);

        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, tricksterSheep, EquipmentSlot.HAND);
        listener.onPlayerInteractSheep(event);

        assertThat(player.hasPotionEffect(PotionEffectType.BLINDNESS)).isTrue();
    }
}
