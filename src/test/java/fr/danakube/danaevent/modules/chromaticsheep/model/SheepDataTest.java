package fr.danakube.danaevent.modules.chromaticsheep.model;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Sheep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SheepDataTest {

    private ServerMock server;
    private WorldMock world;
    private Sheep sheep;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("sheep_data_world");
        sheep = (Sheep) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.SHEEP);
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should accurately tag sheep and retrieve game metadata from PDC")
    void shouldTagAndRetrieveMetadata() {
        assertThat(SheepData.isGameSheep(sheep)).isFalse();
        assertThat(SheepData.getArenaId(sheep)).isEmpty();
        assertThat(SheepData.getSpecialType(sheep)).isEqualTo(SpecialSheepType.NORMAL);

        SheepData.tagSheep(sheep, "pasture_1", SpecialSheepType.GOLDEN);

        assertThat(SheepData.isGameSheep(sheep)).isTrue();
        assertThat(SheepData.getArenaId(sheep)).contains("pasture_1");
        assertThat(SheepData.getSpecialType(sheep)).isEqualTo(SpecialSheepType.GOLDEN);

        SheepData.setSpecialType(sheep, SpecialSheepType.RAINBOW);
        assertThat(SheepData.getSpecialType(sheep)).isEqualTo(SpecialSheepType.RAINBOW);
    }

    @Test
    @DisplayName("Should handle temporary protection countdown")
    void shouldHandleProtectionCountdown() {
        assertThat(SheepData.isProtected(sheep)).isFalse();

        // Protect for 5 seconds in the future
        long future = System.currentTimeMillis() + 5000L;
        SheepData.setProtectedUntil(sheep, future);
        assertThat(SheepData.isProtected(sheep)).isTrue();

        // Expired protection
        long past = System.currentTimeMillis() - 1000L;
        SheepData.setProtectedUntil(sheep, past);
        assertThat(SheepData.isProtected(sheep)).isFalse();
    }

    @Test
    @DisplayName("Should track last dyed holder and timestamp")
    void shouldTrackLastDyed() {
        UUID holder = UUID.randomUUID();
        assertThat(SheepData.getLastDyedBy(sheep)).isEmpty();
        assertThat(SheepData.getLastDyedTime(sheep)).isEqualTo(0L);

        SheepData.setLastDyed(sheep, holder);

        assertThat(SheepData.getLastDyedBy(sheep)).contains(holder);
        assertThat(SheepData.getLastDyedTime(sheep)).isGreaterThan(0L);
    }
}
