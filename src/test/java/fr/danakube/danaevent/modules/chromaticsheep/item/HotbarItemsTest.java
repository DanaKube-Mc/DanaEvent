package fr.danakube.danaevent.modules.chromaticsheep.item;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HotbarItemsTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should create PaintBrush with PDC tag and detect it reliably")
    void shouldCreateAndDetectPaintBrush() {
        ItemStack brush = PaintBrushItem.createItem();
        assertThat(brush.getType()).isEqualTo(Material.BRUSH);
        assertThat(PaintBrushItem.isPaintBrush(brush)).isTrue();

        // Ordinary brush without PDC should be false
        ItemStack ordinaryBrush = new ItemStack(Material.BRUSH);
        assertThat(PaintBrushItem.isPaintBrush(ordinaryBrush)).isFalse();

        // Null / air checks
        assertThat(PaintBrushItem.isPaintBrush(null)).isFalse();
        assertThat(PaintBrushItem.isPaintBrush(new ItemStack(Material.AIR))).isFalse();
    }

    @Test
    @DisplayName("Should create PaintBomb with PDC tag and detect it reliably")
    void shouldCreateAndDetectPaintBomb() {
        ItemStack bomb = PaintBombItem.createItem();
        assertThat(bomb.getType()).isEqualTo(Material.SNOWBALL);
        assertThat(PaintBombItem.isPaintBomb(bomb)).isTrue();

        // Ordinary snowball without PDC should be false
        ItemStack ordinarySnowball = new ItemStack(Material.SNOWBALL);
        assertThat(PaintBombItem.isPaintBomb(ordinarySnowball)).isFalse();

        // Null / air checks
        assertThat(PaintBombItem.isPaintBomb(null)).isFalse();
        assertThat(PaintBombItem.isPaintBomb(new ItemStack(Material.AIR))).isFalse();
    }
}
