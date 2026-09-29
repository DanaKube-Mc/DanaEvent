package fr.danakube.danaevent.core.team.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class TeamColorTest {

    @Test
    @DisplayName("Should have exactly 16 official Minecraft team colors")
    void shouldHave16Colors() {
        assertThat(TeamColor.values()).hasSize(16);
    }

    @ParameterizedTest
    @EnumSource(TeamColor.class)
    @DisplayName("Every TeamColor should have valid non-null properties")
    void shouldHaveNonNullProperties(TeamColor color) {
        assertThat(color.getId()).isNotBlank();
        assertThat(color.getDisplayName()).isNotBlank();
        assertThat(color.getNamedTextColor()).isNotNull();
        assertThat(color.getTextColor()).isNotNull();
        assertThat(color.getBukkitColor()).isNotNull();
        assertThat(color.getWoolMaterial()).isNotNull();
        assertThat(color.getDyeMaterial()).isNotNull();
        assertThat(color.getGlassPaneMaterial()).isNotNull();
    }

    @Test
    @DisplayName("fromString should resolve colors case-insensitively with id or name")
    void shouldResolveFromString() {
        assertThat(TeamColor.fromString("red")).contains(TeamColor.RED);
        assertThat(TeamColor.fromString("RED")).contains(TeamColor.RED);
        assertThat(TeamColor.fromString("light_blue")).contains(TeamColor.LIGHT_BLUE);
        assertThat(TeamColor.fromString("light-blue")).contains(TeamColor.LIGHT_BLUE);
        assertThat(TeamColor.fromString("LIGHT_BLUE")).contains(TeamColor.LIGHT_BLUE);
        assertThat(TeamColor.fromString("invalid_color")).isEmpty();
        assertThat(TeamColor.fromString(null)).isEmpty();
        assertThat(TeamColor.fromString("")).isEmpty();
    }
}
