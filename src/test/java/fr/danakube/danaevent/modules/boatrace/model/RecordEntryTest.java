package fr.danakube.danaevent.modules.boatrace.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordEntryTest {

    @Test
    @DisplayName("Should create RecordEntry with valid fields and getters")
    void shouldCreateRecordEntry() {
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();
        RecordEntry entry = new RecordEntry(1L, "track_ice", uuid, 74285L, 3, "2026-09", now);

        assertThat(entry.id()).isEqualTo(1L);
        assertThat(entry.trackId()).isEqualTo("track_ice");
        assertThat(entry.playerUuid()).isEqualTo(uuid);
        assertThat(entry.timeMillis()).isEqualTo(74285L);
        assertThat(entry.laps()).isEqualTo(3);
        assertThat(entry.periodMonth()).isEqualTo("2026-09");
        assertThat(entry.createdAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Should format time in MM:SS.mmm format")
    void shouldFormatTimeCorrectly() {
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();

        // 74285 ms = 1 min, 14 sec, 285 ms -> 01:14.285
        RecordEntry entry1 = new RecordEntry(1L, "track_ice", uuid, 74285L, 1, "2026-09", now);
        assertThat(entry1.formatTime()).isEqualTo("01:14.285");

        // 0 ms -> 00:00.000
        RecordEntry entry2 = new RecordEntry(2L, "track_ice", uuid, 0L, 1, "2026-09", now);
        assertThat(entry2.formatTime()).isEqualTo("00:00.000");

        // 59999 ms = 00:59.999
        RecordEntry entry3 = new RecordEntry(3L, "track_ice", uuid, 59999L, 1, "2026-09", now);
        assertThat(entry3.formatTime()).isEqualTo("00:59.999");

        // 60000 ms = 01:00.000
        RecordEntry entry4 = new RecordEntry(4L, "track_ice", uuid, 60000L, 1, "2026-09", now);
        assertThat(entry4.formatTime()).isEqualTo("01:00.000");

        // Static formatTime test
        assertThat(RecordEntry.formatTime(125005L)).isEqualTo("02:05.005");
    }

    @Test
    @DisplayName("Should throw NullPointerException if required fields are null")
    void shouldThrowWhenRequiredFieldsNull() {
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();

        assertThatThrownBy(() -> new RecordEntry(1L, null, uuid, 1000L, 1, "2026-09", now))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new RecordEntry(1L, "track1", null, 1000L, 1, "2026-09", now))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new RecordEntry(1L, "track1", uuid, 1000L, 1, null, now))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new RecordEntry(1L, "track1", uuid, 1000L, 1, "2026-09", null))
            .isInstanceOf(NullPointerException.class);
    }
}
