package fr.danakube.danaevent.modules.deacoudre.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.deacoudre.manager.DacJumpCallback;
import fr.danakube.danaevent.modules.deacoudre.manager.DacPoolManager;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameFormat;
import fr.danakube.danaevent.modules.deacoudre.model.DacPlayerSession;
import fr.danakube.danaevent.modules.deacoudre.model.JumpMode;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DacFallListenerTest {

    private ServerMock server;
    private WorldMock world;
    private DacPoolManager poolManager;
    private DacArena arena;
    private DacFallListener fallListener;

    private final Map<UUID, DacPlayerSession> sessions = new HashMap<>();
    private final AtomicBoolean successCalled = new AtomicBoolean(false);
    private final AtomicBoolean isPerfectDac = new AtomicBoolean(false);
    private final AtomicReference<String> failReason = new AtomicReference<>(null);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("dac_fall_world");
        poolManager = new DacPoolManager();

        arena = new DacArena("fall_arena", "Fall Arena", DacGameFormat.SOLO, JumpMode.TURN_BY_TURN);
        arena.setPoolRegion(new CuboidRegion(world.getName(), -5, 50, -5, 5, 50, 5));
        arena.setDivingLocation(new Location(world, 0, 80, 0));

        // Fill pool with water
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                world.getBlockAt(x, 50, z).setType(Material.WATER);
            }
        }

        DacJumpCallback callback = new DacJumpCallback() {
            @Override
            public void onJumpSuccess(@NotNull Player player, @NotNull Block waterBlock, boolean isPerfect) {
                successCalled.set(true);
                isPerfectDac.set(isPerfect);
            }

            @Override
            public void onJumpFail(@NotNull Player player, @NotNull String reason) {
                failReason.set(reason);
            }
        };

        fallListener = new DacFallListener(
            poolManager,
            uuid -> Optional.ofNullable(sessions.get(uuid)),
            uuid -> Optional.of(arena),
            callback
        );
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should cancel any damage for active participants")
    void testDamageCancellation() {
        PlayerMock player = server.addPlayer("Jumper");
        DacPlayerSession session = new DacPlayerSession(
            player.getUniqueId(), arena.getId(), DyeColor.LIGHT_BLUE, player.getUniqueId(), false, 3
        );
        sessions.put(player.getUniqueId(), session);

        EntityDamageEvent event = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL, 10.0);
        fallListener.onEntityDamage(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Should trigger onJumpSuccess and convert water block to wool when landing in pool")
    void testSuccessfulWaterLanding() {
        PlayerMock player = server.addPlayer("Jumper");
        DacPlayerSession session = new DacPlayerSession(
            player.getUniqueId(), arena.getId(), DyeColor.MAGENTA, player.getUniqueId(), false, 3
        );
        session.setJumping(true);
        sessions.put(player.getUniqueId(), session);

        Location from = new Location(world, 0, 70, 0);
        Location to = new Location(world, 0, 50.2, 0);

        PlayerMoveEvent moveEvent = new PlayerMoveEvent(player, from, to);
        fallListener.onPlayerMove(moveEvent);

        assertThat(successCalled.get()).isTrue();
        assertThat(isPerfectDac.get()).isFalse();
        assertThat(world.getBlockAt(0, 50, 0).getType()).isEqualTo(Material.MAGENTA_WOOL);
    }

    @Test
    @DisplayName("Should trigger onJumpSuccess with isPerfect=true when landing in 1x1 surrounded by wool")
    void testPerfectDacLanding() {
        PlayerMock player = server.addPlayer("Jumper");
        DacPlayerSession session = new DacPlayerSession(
            player.getUniqueId(), arena.getId(), DyeColor.CYAN, player.getUniqueId(), false, 3
        );
        session.setJumping(true);
        sessions.put(player.getUniqueId(), session);

        // Surround target (0, 50, 0) with wool
        world.getBlockAt(0, 50, -1).setType(Material.RED_WOOL);
        world.getBlockAt(0, 50, 1).setType(Material.BLUE_WOOL);
        world.getBlockAt(1, 50, 0).setType(Material.LIME_WOOL);
        world.getBlockAt(-1, 50, 0).setType(Material.YELLOW_WOOL);

        Location from = new Location(world, 0, 70, 0);
        Location to = new Location(world, 0, 50.2, 0);

        PlayerMoveEvent moveEvent = new PlayerMoveEvent(player, from, to);
        fallListener.onPlayerMove(moveEvent);

        assertThat(successCalled.get()).isTrue();
        assertThat(isPerfectDac.get()).isTrue();
        assertThat(world.getBlockAt(0, 50, 0).getType()).isEqualTo(Material.CYAN_WOOL);
    }

    @Test
    @DisplayName("Should trigger onJumpFail when landing on wool inside the pool")
    void testFailLandingOnWool() {
        PlayerMock player = server.addPlayer("Jumper");
        DacPlayerSession session = new DacPlayerSession(
            player.getUniqueId(), arena.getId(), DyeColor.ORANGE, player.getUniqueId(), false, 3
        );
        session.setJumping(true);
        sessions.put(player.getUniqueId(), session);

        // Set target block to wool
        world.getBlockAt(0, 50, 0).setType(Material.RED_WOOL);

        Location from = new Location(world, 0, 70, 0);
        Location to = new Location(world, 0, 50.5, 0);

        PlayerMoveEvent moveEvent = new PlayerMoveEvent(player, from, to);
        fallListener.onPlayerMove(moveEvent);

        assertThat(failReason.get()).isNotNull();
        assertThat(successCalled.get()).isFalse();
    }

    @Test
    @DisplayName("Should ignore movements from non-jumping players")
    void testIgnoreNonJumpingPlayers() {
        PlayerMock player = server.addPlayer("Spectator");
        DacPlayerSession session = new DacPlayerSession(
            player.getUniqueId(), arena.getId(), DyeColor.WHITE, player.getUniqueId(), false, 3
        );
        session.setJumping(false);
        sessions.put(player.getUniqueId(), session);

        Location from = new Location(world, 0, 70, 0);
        Location to = new Location(world, 0, 50.2, 0);

        PlayerMoveEvent moveEvent = new PlayerMoveEvent(player, from, to);
        fallListener.onPlayerMove(moveEvent);

        assertThat(successCalled.get()).isFalse();
        assertThat(failReason.get()).isNull();
    }
}
