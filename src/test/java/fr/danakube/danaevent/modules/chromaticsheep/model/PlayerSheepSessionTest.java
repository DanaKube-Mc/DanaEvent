package fr.danakube.danaevent.modules.chromaticsheep.model;

import org.bukkit.DyeColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerSheepSessionTest {

    @Test
    @DisplayName("Should track player and team effective holder correctly")
    void shouldTrackHolderAccurately() {
        UUID playerUuid = UUID.randomUUID();
        UUID teamUuid = UUID.randomUUID();

        // Solo session
        PlayerSheepSession soloSession = new PlayerSheepSession(playerUuid, null, DyeColor.RED, "arena_1");
        assertThat(soloSession.getPlayerUuid()).isEqualTo(playerUuid);
        assertThat(soloSession.getTeamUuid()).isNull();
        assertThat(soloSession.isTeam()).isFalse();
        assertThat(soloSession.getEffectiveHolderUuid()).isEqualTo(playerUuid);
        assertThat(soloSession.getColor()).isEqualTo(DyeColor.RED);
        assertThat(soloSession.getArenaId()).isEqualTo("arena_1");

        // Team session
        PlayerSheepSession teamSession = new PlayerSheepSession(playerUuid, teamUuid, DyeColor.BLUE, "arena_1");
        assertThat(teamSession.isTeam()).isTrue();
        assertThat(teamSession.getEffectiveHolderUuid()).isEqualTo(teamUuid);
    }

    @Test
    @DisplayName("Should manage bomb cooldown accurately")
    void shouldManageBombCooldown() {
        UUID playerUuid = UUID.randomUUID();
        PlayerSheepSession session = new PlayerSheepSession(playerUuid, null, DyeColor.GREEN, "arena_1");

        // Initially ready
        assertThat(session.canThrowBomb()).isTrue();
        assertThat(session.getRemainingBombCooldownSeconds()).isEqualTo(0.0);

        // Record throw
        session.recordBombThrow();
        assertThat(session.canThrowBomb()).isFalse();
        assertThat(session.getRemainingBombCooldownSeconds()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should manage score points properly")
    void shouldManageScore() {
        UUID playerUuid = UUID.randomUUID();
        PlayerSheepSession session = new PlayerSheepSession(playerUuid, null, DyeColor.YELLOW, "arena_1");

        assertThat(session.getScorePoints()).isEqualTo(0);
        session.addScore(15);
        assertThat(session.getScorePoints()).isEqualTo(15);
        session.setScorePoints(50);
        assertThat(session.getScorePoints()).isEqualTo(50);
    }
}
