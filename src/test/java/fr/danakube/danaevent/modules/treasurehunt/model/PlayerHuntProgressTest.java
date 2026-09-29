package fr.danakube.danaevent.modules.treasurehunt.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerHuntProgressTest {

    @Test
    @DisplayName("Should initialize progress and track active steps accurately")
    void shouldTrackProgressAccurately() {
        UUID playerUuid = UUID.randomUUID();
        List<Integer> order = List.of(2, 4, 1, 3);
        PlayerHuntProgress progress = PlayerHuntProgress.start(playerUuid, false, "pyramid_escape", order);

        assertThat(progress.getHolderUuid()).isEqualTo(playerUuid);
        assertThat(progress.isTeam()).isFalse();
        assertThat(progress.getHuntId()).isEqualTo("pyramid_escape");
        assertThat(progress.getCurrentStepIndex()).isEqualTo(0);
        assertThat(progress.getActiveStepNumber()).isEqualTo(2);
        assertThat(progress.isLastStep()).isFalse();
        assertThat(progress.isCompleted()).isFalse();

        // Advance to step 4
        progress.advanceStep();
        assertThat(progress.getCurrentStepIndex()).isEqualTo(1);
        assertThat(progress.getActiveStepNumber()).isEqualTo(4);
        assertThat(progress.isLastStep()).isFalse();

        // Advance to step 1
        progress.advanceStep();
        assertThat(progress.getActiveStepNumber()).isEqualTo(1);
        assertThat(progress.isLastStep()).isFalse();

        // Advance to step 3 (last step)
        progress.advanceStep();
        assertThat(progress.getActiveStepNumber()).isEqualTo(3);
        assertThat(progress.isLastStep()).isTrue();

        // Complete
        long finishTime = progress.getStartTimeMillis() + 45200L;
        progress.markCompleted(finishTime);
        assertThat(progress.isCompleted()).isTrue();
        assertThat(progress.getElapsedTimeMillis()).isEqualTo(45200L);
        assertThat(progress.formatElapsedTime()).isEqualTo("00:45.200");
    }

    @Test
    @DisplayName("Should handle empty step order safely")
    void shouldHandleEmptyStepOrder() {
        UUID playerUuid = UUID.randomUUID();
        PlayerHuntProgress progress = PlayerHuntProgress.start(playerUuid, true, "empty_hunt", List.of());

        assertThat(progress.getActiveStepNumber()).isEqualTo(-1);
        assertThat(progress.isLastStep()).isFalse();
    }
}
