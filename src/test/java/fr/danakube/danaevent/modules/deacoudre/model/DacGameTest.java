package fr.danakube.danaevent.modules.deacoudre.model;

import org.bukkit.DyeColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DacGameTest {

    @Test
    @DisplayName("Should manage game states, participants, colors and turn queue correctly")
    void testGameLifecycleAndQueue() {
        DacArena arena = new DacArena("arena1", "Arena 1", DacGameFormat.SOLO, JumpMode.TURN_BY_TURN);
        DacGame game = new DacGame(arena);

        assertThat(game.getState()).isEqualTo(DacGameState.WAITING);
        game.setState(DacGameState.STARTING);
        assertThat(game.getState()).isEqualTo(DacGameState.STARTING);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        DacPlayerSession s1 = new DacPlayerSession(p1, "arena1", DyeColor.BLUE, p1, false, 3);
        DacPlayerSession s2 = new DacPlayerSession(p2, "arena1", DyeColor.RED, p2, false, 3);

        game.addParticipant(s1);
        game.addParticipant(s2);

        assertThat(game.getParticipants()).hasSize(2);
        assertThat(game.isColorUsed(DyeColor.BLUE)).isTrue();
        assertThat(game.isColorUsed(DyeColor.GREEN)).isFalse();
        assertThat(game.getAlivePlayers()).containsExactlyInAnyOrder(p1, p2);

        // Initialize turn queue
        game.initializeTurnQueue();
        assertThat(game.getTurnQueue()).hasSize(2);
        assertThat(game.getCurrentRound()).isEqualTo(1);

        UUID jumper1 = game.advanceNextJumper();
        assertThat(jumper1).isNotNull();
        assertThat(game.getCurrentJumper()).isEqualTo(jumper1);

        UUID jumper2 = game.advanceNextJumper();
        assertThat(jumper2).isNotNull();
        assertThat(jumper2).isNotEqualTo(jumper1);

        // Third advance loops back and increments round
        UUID jumper3 = game.advanceNextJumper();
        assertThat(jumper3).isEqualTo(jumper1);
        assertThat(game.getCurrentRound()).isEqualTo(2);

        // Eliminate s1
        s1.setSpectator(true);
        assertThat(game.getAlivePlayers()).containsExactly(p2);

        // Advance should now skip s1 and only return s2
        UUID jumper4 = game.advanceNextJumper();
        assertThat(jumper4).isEqualTo(p2);

        // Remove participant
        game.removeParticipant(p2);
        assertThat(game.getParticipants()).hasSize(1);
        assertThat(game.getTurnQueue()).doesNotContain(p2);
    }

    @Test
    @DisplayName("Should handle team lives in SHARED_POOL mode")
    void testSharedPoolLives() {
        DacArena arena = new DacArena("arena_team", "Team Arena", DacGameFormat.TEAM, JumpMode.TURN_BY_TURN);
        arena.setTeamLifeMode(DacTeamLifeMode.SHARED_POOL);
        DacGame game = new DacGame(arena);

        UUID teamUuid = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        DacPlayerSession s1 = new DacPlayerSession(p1, "arena_team", DyeColor.BLUE, teamUuid, true, 3);
        DacPlayerSession s2 = new DacPlayerSession(p2, "arena_team", DyeColor.BLUE, teamUuid, true, 3);

        game.addParticipant(s1);
        game.addParticipant(s2);

        game.setTeamLives(teamUuid, 2);
        assertThat(game.getTeamLives(teamUuid)).isEqualTo(2);

        // Decrement 1
        assertThat(game.decrementTeamLife(teamUuid)).isTrue();
        assertThat(game.getTeamLives(teamUuid)).isEqualTo(1);
        assertThat(s1.isSpectator()).isFalse();

        // Increment 1
        assertThat(game.incrementTeamLife(teamUuid, 5)).isTrue();
        assertThat(game.getTeamLives(teamUuid)).isEqualTo(2);

        // Decrement 2
        game.decrementTeamLife(teamUuid);
        game.decrementTeamLife(teamUuid);
        assertThat(game.getTeamLives(teamUuid)).isEqualTo(0);

        // Both players in team should now be spectators
        assertThat(s1.isSpectator()).isTrue();
        assertThat(s2.isSpectator()).isTrue();
        assertThat(game.getAliveTeams()).isEmpty();
    }

    @Test
    @DisplayName("Should manage wave mode completion and reset")
    void testWaveModeMechanics() {
        DacArena arena = new DacArena("arena_wave", "Wave Arena", DacGameFormat.SOLO, JumpMode.SIMULTANEOUS_WAVE);
        DacGame game = new DacGame(arena);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        game.addParticipant(new DacPlayerSession(p1, "arena_wave", DyeColor.BLUE, p1, false, 3));
        game.addParticipant(new DacPlayerSession(p2, "arena_wave", DyeColor.RED, p2, false, 3));

        assertThat(game.isWaveComplete()).isFalse();

        game.recordWaveJump(p1);
        assertThat(game.isWaveComplete()).isFalse();

        game.recordWaveJump(p2);
        assertThat(game.isWaveComplete()).isTrue();

        game.resetWave();
        assertThat(game.isWaveComplete()).isFalse();
    }
}
