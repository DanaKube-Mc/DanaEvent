package fr.danakube.danaevent.modules.boatrace.model;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import fr.danakube.danaevent.modules.boatrace.model.Track;
import fr.danakube.danaevent.modules.boatrace.model.TrackMode;
import fr.danakube.danaevent.modules.boatrace.model.TrackType;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RaceSessionTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("race_test");
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should initialize RaceSession with proper default values and parameters")
    void shouldInitializeRaceSession() {
        UUID playerUuid = UUID.randomUUID();
        Track track = new Track("speedway", "Speedway", TrackType.CIRCUIT_LAPS, TrackMode.TIME_ATTACK_247);
        track.setLaps(3);
        Boat boat = world.spawn(new Location(world, 0, 64, 0), Boat.class);
        UUID boatUuid = boat.getUniqueId();

        long now = System.currentTimeMillis();
        RaceSession session = new RaceSession(playerUuid, track, boat, now, RaceState.RACING);

        assertThat(session.getPlayerUuid()).isEqualTo(playerUuid);
        assertThat(session.getTrack()).isEqualTo(track);
        assertThat(session.getBoat()).isEqualTo(boat);
        assertThat(session.getBoatUuid()).isEqualTo(boatUuid);
        assertThat(session.getStartTimeMillis()).isEqualTo(now);
        assertThat(session.getState()).isEqualTo(RaceState.RACING);
        assertThat(session.getCurrentLap()).isEqualTo(1);
        assertThat(session.getLapTimes()).isEmpty();
        assertThat(session.getLastLapCrossingMillis()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should correctly compute elapsed time in milliseconds")
    void shouldComputeElapsedTime() throws InterruptedException {
        UUID playerUuid = UUID.randomUUID();
        Track track = new Track("speedway", "Speedway", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
        long startTime = System.currentTimeMillis() - 500;

        RaceSession session = new RaceSession(playerUuid, track, null, startTime, RaceState.RACING);

        assertThat(session.getElapsedTimeMillis()).isGreaterThanOrEqualTo(500);
    }

    @Test
    @DisplayName("Should format time in MM:SS.mmm format")
    void shouldFormatTimeCorrectly() {
        assertThat(RaceSession.formatTime(0)).isEqualTo("00:00.000");
        assertThat(RaceSession.formatTime(74285)).isEqualTo("01:14.285");
        assertThat(RaceSession.formatTime(3661005)).isEqualTo("61:01.005");
        assertThat(RaceSession.formatTime(-10)).isEqualTo("00:00.000");
    }

    @Test
    @DisplayName("Should debounce line crossing within the configured interval")
    void shouldDebounceLineCrossing() {
        UUID playerUuid = UUID.randomUUID();
        Track track = new Track("speedway", "Speedway", TrackType.CIRCUIT_LAPS, TrackMode.TIME_ATTACK_247);
        long start = 10000L;
        RaceSession session = new RaceSession(playerUuid, track, null, start, RaceState.RACING);
        session.setLastLapCrossingMillis(start);

        // Immediate check (within 2000 ms debounce window)
        assertThat(session.canCrossLine(2000, start + 500)).isFalse();
        assertThat(session.canCrossLine(2000, start + 1999)).isFalse();

        // After debounce interval
        assertThat(session.canCrossLine(2000, start + 2000)).isTrue();
        assertThat(session.canCrossLine(2000, start + 3500)).isTrue();
    }

    @Test
    @DisplayName("Should handle lap recording and state transitions")
    void shouldHandleLapsAndStateTransitions() {
        UUID playerUuid = UUID.randomUUID();
        Track track = new Track("speedway", "Speedway", TrackType.CIRCUIT_LAPS, TrackMode.TIME_ATTACK_247);
        track.setLaps(2);
        long start = 10000L;

        RaceSession session = new RaceSession(playerUuid, track, null, start, RaceState.RACING);
        assertThat(session.getCurrentLap()).isEqualTo(1);

        session.recordLap(25000L);
        assertThat(session.getLapTimes()).containsExactly(25000L);
        session.setCurrentLap(2);
        assertThat(session.getCurrentLap()).isEqualTo(2);

        session.setState(RaceState.FINISHED);
        assertThat(session.getState()).isEqualTo(RaceState.FINISHED);

        session.setState(RaceState.CANCELLED);
        assertThat(session.getState()).isEqualTo(RaceState.CANCELLED);
    }

    @Test
    @DisplayName("Should reject null mandatory parameters")
    void shouldRejectNullMandatoryParameters() {
        Track track = new Track("track", "Track", TrackType.SPRINT, TrackMode.TIME_ATTACK_247);
        assertThatThrownBy(() -> new RaceSession(null, track, null, System.currentTimeMillis(), RaceState.RACING))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RaceSession(UUID.randomUUID(), null, null, System.currentTimeMillis(), RaceState.RACING))
            .isInstanceOf(NullPointerException.class);
    }
}
