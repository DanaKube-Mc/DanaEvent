package fr.danakube.danaevent.modules.deacoudre.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.block.BlockMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.deacoudre.manager.DacPoolManager;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameFormat;
import fr.danakube.danaevent.modules.deacoudre.model.DacPlayerSession;
import fr.danakube.danaevent.modules.deacoudre.model.JumpMode;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DacProtectionListenerTest {

    private ServerMock server;
    private WorldMock world;
    private DacPoolManager poolManager;
    private DacArena arena;
    private DacProtectionListener protectionListener;

    private final Map<UUID, DacPlayerSession> sessions = new HashMap<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("dac_protection_world");
        poolManager = new DacPoolManager();

        arena = new DacArena("prot_arena", "Protection Arena", DacGameFormat.SOLO, JumpMode.TURN_BY_TURN);
        arena.setPoolRegion(new CuboidRegion(world.getName(), -2, 50, -2, 2, 50, 2));

        protectionListener = new DacProtectionListener(
            poolManager,
            uuid -> Optional.ofNullable(sessions.get(uuid)),
            () -> List.of(arena)
        );
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should cancel block break inside pool region or by participant")
    void testBlockBreakProtection() {
        PlayerMock participant = server.addPlayer("Participant");
        sessions.put(participant.getUniqueId(), new DacPlayerSession(
            participant.getUniqueId(), arena.getId(), DyeColor.RED, participant.getUniqueId(), false, 3
        ));

        Block outsideBlock = world.getBlockAt(100, 64, 100);
        BlockBreakEvent event1 = new BlockBreakEvent(outsideBlock, participant);
        protectionListener.onBlockBreak(event1);
        assertThat(event1.isCancelled()).isTrue();

        PlayerMock outsider = server.addPlayer("Outsider");
        Block poolBlock = world.getBlockAt(0, 50, 0);
        BlockBreakEvent event2 = new BlockBreakEvent(poolBlock, outsider);
        protectionListener.onBlockBreak(event2);
        assertThat(event2.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Should cancel block place inside pool region or by participant")
    void testBlockPlaceProtection() {
        PlayerMock participant = server.addPlayer("Participant");
        sessions.put(participant.getUniqueId(), new DacPlayerSession(
            participant.getUniqueId(), arena.getId(), DyeColor.RED, participant.getUniqueId(), false, 3
        ));

        BlockMock placedBlock = world.getBlockAt(0, 50, 0);
        Block placedAgainst = world.getBlockAt(0, 49, 0);
        ItemStack itemInHand = new ItemStack(Material.STONE);

        BlockPlaceEvent event1 = new BlockPlaceEvent(placedBlock, placedBlock.getState(), placedAgainst, itemInHand, participant, true, EquipmentSlot.HAND);
        protectionListener.onBlockPlace(event1);
        assertThat(event1.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Should cancel item drops for active participants")
    void testItemDropProtection() {
        PlayerMock participant = server.addPlayer("Participant");
        sessions.put(participant.getUniqueId(), new DacPlayerSession(
            participant.getUniqueId(), arena.getId(), DyeColor.RED, participant.getUniqueId(), false, 3
        ));

        PlayerDropItemEvent event = new PlayerDropItemEvent(participant, null);
        protectionListener.onPlayerDropItem(event);
        assertThat(event.isCancelled()).isTrue();
    }
}
