package fr.danakube.danaevent.core.selection;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CuboidRegionTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Nested
    @DisplayName("Constructors & Dimension Normalization")
    class ConstructorTests {

        @Test
        @DisplayName("Should automatically normalize min and max when created with inverted coordinates")
        void shouldNormalizeMinAndMaxCoordinates() {
            CuboidRegion region = new CuboidRegion("world", 10, 20, 30, -5, 5, 15);

            assertThat(region.getWorldName()).isEqualTo("world");
            assertThat(region.getMinX()).isEqualTo(-5);
            assertThat(region.getMinY()).isEqualTo(5);
            assertThat(region.getMinZ()).isEqualTo(15);
            assertThat(region.getMaxX()).isEqualTo(10);
            assertThat(region.getMaxY()).isEqualTo(20);
            assertThat(region.getMaxZ()).isEqualTo(30);

            assertThat(region.getWidthX()).isEqualTo(16);
            assertThat(region.getHeightY()).isEqualTo(16);
            assertThat(region.getWidthZ()).isEqualTo(16);
        }

        @Test
        @DisplayName("Should construct from two Bukkit locations in same world")
        void shouldConstructFromLocations() {
            Location loc1 = new Location(world, 100, 64, -50);
            Location loc2 = new Location(world, 50, 70, 0);

            CuboidRegion region = new CuboidRegion(loc1, loc2);

            assertThat(region.getWorldName()).isEqualTo("world");
            assertThat(region.getMinX()).isEqualTo(50);
            assertThat(region.getMinY()).isEqualTo(64);
            assertThat(region.getMinZ()).isEqualTo(-50);
            assertThat(region.getMaxX()).isEqualTo(100);
            assertThat(region.getMaxY()).isEqualTo(70);
            assertThat(region.getMaxZ()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should reject locations in different worlds or null worlds")
        void shouldRejectInvalidLocations() {
            WorldMock otherWorld = server.addSimpleWorld("world_nether");
            Location loc1 = new Location(world, 0, 0, 0);
            Location loc2 = new Location(otherWorld, 10, 10, 10);
            Location locNoWorld = new Location(null, 0, 0, 0);

            assertThatThrownBy(() -> new CuboidRegion(loc1, loc2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same world");

            assertThatThrownBy(() -> new CuboidRegion(loc1, locNoWorld))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid world");

            assertThatThrownBy(() -> new CuboidRegion(null, loc1))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Should reject null world name")
        void shouldRejectNullWorldName() {
            assertThatThrownBy(() -> new CuboidRegion(null, 0, 0, 0, 1, 1, 1))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Volume & Geometric Computations")
    class VolumeAndGeometryTests {

        @Test
        @DisplayName("Single block cuboid should have volume of 1 and centered coordinates (+0.5)")
        void singleBlockVolumeAndCenter() {
            CuboidRegion region = new CuboidRegion("world", 5, 10, 15, 5, 10, 15);

            assertThat(region.getVolume()).isEqualTo(1L);
            assertThat(region.getWidthX()).isEqualTo(1);
            assertThat(region.getHeightY()).isEqualTo(1);
            assertThat(region.getWidthZ()).isEqualTo(1);

            Location center = region.getCenter(world);
            assertThat(center.getX()).isEqualTo(5.5);
            assertThat(center.getY()).isEqualTo(10.5);
            assertThat(center.getZ()).isEqualTo(15.5);
            assertThat(center.getWorld()).isEqualTo(world);
        }

        @Test
        @DisplayName("Cuboid volume should correctly calculate total included blocks without overflow")
        void shouldCalculateVolumeAccurately() {
            CuboidRegion region = new CuboidRegion("world", 0, 0, 0, 9, 9, 9);
            assertThat(region.getVolume()).isEqualTo(1000L);

            // Large cuboid calculation test (1000 x 256 x 1000 = 256,000,000)
            CuboidRegion largeRegion = new CuboidRegion("world", 0, 0, 0, 999, 255, 999);
            assertThat(largeRegion.getVolume()).isEqualTo(256_000_000L);
        }

        @Test
        @DisplayName("Should return accurate min and max locations")
        void shouldReturnMinAndMaxLocations() {
            CuboidRegion region = new CuboidRegion("world", -10, 20, -30, 10, 40, 30);

            Location minLoc = region.getMinLocation(world);
            Location maxLoc = region.getMaxLocation(world);

            assertThat(minLoc).isEqualTo(new Location(world, -10, 20, -30));
            assertThat(maxLoc).isEqualTo(new Location(world, 10, 40, 30));
        }

        @Test
        @DisplayName("Should throw when accessing geometry with mismatched world")
        void shouldRejectMismatchedWorldInGetters() {
            CuboidRegion region = new CuboidRegion("world", 0, 0, 0, 5, 5, 5);
            WorldMock otherWorld = server.addSimpleWorld("other_world");

            assertThatThrownBy(() -> region.getCenter(otherWorld))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> region.getMinLocation(otherWorld))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> region.getMaxLocation(otherWorld))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Contains Checks")
    class ContainsTests {

        private CuboidRegion region;

        @BeforeEach
        void createRegion() {
            region = new CuboidRegion("world", 10, 20, 30, 20, 30, 40);
        }

        @Test
        @DisplayName("Should contain points inside the bounds")
        void shouldContainInsidePoints() {
            assertThat(region.contains(15, 25, 35)).isTrue();
            assertThat(region.contains(new Location(world, 15, 25, 35))).isTrue();
        }

        @Test
        @DisplayName("Should contain points on the exact boundaries (corners and edges)")
        void shouldContainBoundaryPoints() {
            // Min and Max corners
            assertThat(region.contains(10, 20, 30)).isTrue();
            assertThat(region.contains(20, 30, 40)).isTrue();

            // Faces
            assertThat(region.contains(10, 25, 35)).isTrue();
            assertThat(region.contains(20, 25, 35)).isTrue();
            assertThat(region.contains(15, 20, 35)).isTrue();
            assertThat(region.contains(15, 30, 35)).isTrue();
            assertThat(region.contains(15, 25, 30)).isTrue();
            assertThat(region.contains(15, 25, 40)).isTrue();
        }

        @Test
        @DisplayName("Should not contain points outside boundaries")
        void shouldNotContainOutsidePoints() {
            assertThat(region.contains(9, 25, 35)).isFalse();
            assertThat(region.contains(21, 25, 35)).isFalse();
            assertThat(region.contains(15, 19, 35)).isFalse();
            assertThat(region.contains(15, 31, 35)).isFalse();
            assertThat(region.contains(15, 25, 29)).isFalse();
            assertThat(region.contains(15, 25, 41)).isFalse();
        }

        @Test
        @DisplayName("Should return false for locations in another world or null")
        void shouldReturnFalseForDifferentWorldOrNull() {
            WorldMock nether = server.addSimpleWorld("world_nether");
            Location netherLoc = new Location(nether, 15, 25, 35);
            Location nullWorldLoc = new Location(null, 15, 25, 35);

            assertThat(region.contains(netherLoc)).isFalse();
            assertThat(region.contains(nullWorldLoc)).isFalse();
            assertThat(region.contains((Location) null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Bukkit Configuration Serialization")
    class SerializationTests {

        @Test
        @DisplayName("Should serialize and deserialize through Map correctly")
        void shouldSerializeAndDeserializeMap() {
            CuboidRegion original = new CuboidRegion("world", -5, 10, 20, 15, 30, 50);

            Map<String, Object> serialized = original.serialize();
            assertThat(serialized).containsEntry("world", "world")
                .containsEntry("minX", -5)
                .containsEntry("minY", 10)
                .containsEntry("minZ", 20)
                .containsEntry("maxX", 15)
                .containsEntry("maxY", 30)
                .containsEntry("maxZ", 50);

            CuboidRegion deserialized = CuboidRegion.deserialize(serialized);
            assertThat(deserialized).isEqualTo(original);
        }

        @Test
        @DisplayName("Should serialize and deserialize seamlessly through Bukkit YamlConfiguration")
        void shouldRoundtripViaYamlConfiguration() throws Exception {
            CuboidRegion original = new CuboidRegion("world", 10, 60, -20, 30, 80, 0);

            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("event.arena.bounds", original);

            String savedString = yaml.saveToString();
            assertThat(savedString).contains("CuboidRegion");

            YamlConfiguration loadedYaml = new YamlConfiguration();
            loadedYaml.loadFromString(savedString);

            Object obj = loadedYaml.get("event.arena.bounds");
            assertThat(obj).isInstanceOf(CuboidRegion.class);
            assertThat((CuboidRegion) obj).isEqualTo(original);
        }
    }
}
