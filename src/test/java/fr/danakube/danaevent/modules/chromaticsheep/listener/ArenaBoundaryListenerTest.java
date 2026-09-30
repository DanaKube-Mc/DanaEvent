package fr.danakube.danaevent.modules.chromaticsheep.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.chromaticsheep.config.ArenaConfig;
import fr.danakube.danaevent.modules.chromaticsheep.manager.ArenaManager;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameFormat;
import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepArena;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepData;
import fr.danakube.danaevent.modules.chromaticsheep.model.SpecialSheepType;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Sheep;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ArenaBoundaryListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private ArenaManager arenaManager;
    private SheepArena arena;
    private ArenaBoundaryListener listener;
    private final Map<UUID, SheepArena> activePlayers = new ConcurrentHashMap<>();

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("boundary_world");
        player = server.addPlayer();

        ArenaConfig config = new ArenaConfig(new File(tempDir.toFile(), "arenas.yml"));
        arenaManager = new ArenaManager(config);

        arena = arenaManager.createArena("corral", "Corral", GameFormat.SOLO, ScoringMode.FINAL_COUNT);
        arena.setBounds(new CuboidRegion(
            new Location(world, 0, 60, 0),
            new Location(world, 20, 80, 20)
        ));

        listener = new ArenaBoundaryListener(arenaManager, uuid -> Optional.ofNullable(activePlayers.get(uuid)));
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Player in game moving outside cuboid is restricted to arena bounds")
    void shouldRestrainPlayerWithinBounds() {
        activePlayers.put(player.getUniqueId(), arena);

        Location insideFrom = new Location(world, 10, 65, 10);
        Location outsideTo = new Location(world, 25, 65, 10);

        PlayerMoveEvent event = new PlayerMoveEvent(player, insideFrom, outsideTo);
        listener.onPlayerMove(event);

        // Should reset to from location
        assertThat(event.getTo()).isEqualTo(insideFrom);

        // Player moving inside bounds should proceed normally
        Location insideTo = new Location(world, 12, 65, 10);
        PlayerMoveEvent validEvent = new PlayerMoveEvent(player, insideFrom, insideTo);
        listener.onPlayerMove(validEvent);
        assertThat(validEvent.getTo()).isEqualTo(insideTo);
    }

    @Test
    @DisplayName("Non-participating player is unrestricted by boundary listener")
    void shouldIgnoreNonParticipants() {
        Location insideFrom = new Location(world, 10, 65, 10);
        Location outsideTo = new Location(world, 50, 65, 10);

        PlayerMoveEvent event = new PlayerMoveEvent(player, insideFrom, outsideTo);
        listener.onPlayerMove(event);

        assertThat(event.getTo()).isEqualTo(outsideTo);
    }

    @Test
    @DisplayName("Sheep wandering outside bounds is confined back into arena center")
    void shouldConfineSheep() {
        Sheep sheep = (Sheep) world.spawnEntity(new Location(world, 100, 65, 100), EntityType.SHEEP);
        SheepData.tagSheep(sheep, "corral", SpecialSheepType.NORMAL);

        boolean confined = listener.checkAndConfineSheep(sheep);
        assertThat(confined).isTrue();

        Location center = arena.getBounds().getCenter(world);
        assertThat(sheep.getLocation().getBlockX()).isEqualTo(center.getBlockX());
        assertThat(sheep.getLocation().getBlockZ()).isEqualTo(center.getBlockZ());
    }
}
