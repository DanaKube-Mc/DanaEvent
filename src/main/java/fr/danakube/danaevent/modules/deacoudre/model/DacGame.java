package fr.danakube.danaevent.modules.deacoudre.model;

import org.bukkit.DyeColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an active match on a Dé à Coudre arena.
 */
public class DacGame {

    private final DacArena arena;
    private DacGameState state;
    private final Map<UUID, DacPlayerSession> participants = new ConcurrentHashMap<>();
    private final List<UUID> turnQueue = new ArrayList<>();
    private final Set<UUID> waveJumpedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> teamLives = new ConcurrentHashMap<>();

    private int currentQueueIndex = 0;
    private UUID currentJumper = null;
    private int currentRound = 1;
    private int waveNumber = 1;

    public DacGame(@NotNull DacArena arena) {
        this.arena = Objects.requireNonNull(arena, "arena cannot be null");
        this.state = DacGameState.WAITING;
    }

    public @NotNull DacArena getArena() {
        return arena;
    }

    public @NotNull DacGameState getState() {
        return state;
    }

    public void setState(@NotNull DacGameState state) {
        this.state = Objects.requireNonNull(state, "state cannot be null");
    }

    public @NotNull Map<UUID, DacPlayerSession> getParticipants() {
        return participants;
    }

    public @Nullable DacPlayerSession getSession(@NotNull UUID playerUuid) {
        return participants.get(playerUuid);
    }

    public void addParticipant(@NotNull DacPlayerSession session) {
        Objects.requireNonNull(session, "session cannot be null");
        participants.put(session.getPlayerUuid(), session);
    }

    public void removeParticipant(@NotNull UUID playerUuid) {
        participants.remove(playerUuid);
        turnQueue.remove(playerUuid);
        waveJumpedPlayers.remove(playerUuid);
        if (playerUuid.equals(currentJumper)) {
            currentJumper = null;
        }
    }

    public boolean isColorUsed(@NotNull DyeColor color) {
        for (DacPlayerSession session : participants.values()) {
            if (session.getColor() == color) {
                return true;
            }
        }
        return false;
    }

    public @NotNull List<UUID> getAlivePlayers() {
        List<UUID> alive = new ArrayList<>();
        for (DacPlayerSession session : participants.values()) {
            if (session.isAlive()) {
                alive.add(session.getPlayerUuid());
            }
        }
        return alive;
    }

    public @NotNull List<UUID> getAliveTeams() {
        Set<UUID> aliveTeams = new HashSet<>();
        for (DacPlayerSession session : participants.values()) {
            if (session.isAlive()) {
                aliveTeams.add(session.getEffectiveHolderUuid());
            }
        }
        return new ArrayList<>(aliveTeams);
    }

    public void initializeTurnQueue() {
        turnQueue.clear();
        turnQueue.addAll(participants.keySet());
        Collections.shuffle(turnQueue);
        currentQueueIndex = 0;
        currentRound = 1;
        currentJumper = null;
    }

    public @NotNull List<UUID> getTurnQueue() {
        return Collections.unmodifiableList(turnQueue);
    }

    public @Nullable UUID getCurrentJumper() {
        return currentJumper;
    }

    public void setCurrentJumper(@Nullable UUID currentJumper) {
        this.currentJumper = currentJumper;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public int getWaveNumber() {
        return waveNumber;
    }

    public void incrementWaveNumber() {
        this.waveNumber++;
    }

    /**
     * Advances to the next alive jumper in turnQueue.
     * Loops back to the start and increments currentRound if end of queue is reached.
     * Returns null if no alive player is found.
     */
    public @Nullable UUID advanceNextJumper() {
        if (turnQueue.isEmpty()) {
            currentJumper = null;
            return null;
        }

        int attempts = 0;
        int maxAttempts = turnQueue.size();

        while (attempts < maxAttempts) {
            if (currentQueueIndex >= turnQueue.size()) {
                currentQueueIndex = 0;
                currentRound++;
            }

            UUID nextUuid = turnQueue.get(currentQueueIndex);
            currentQueueIndex++;
            attempts++;

            DacPlayerSession session = participants.get(nextUuid);
            if (session != null && session.isAlive()) {
                currentJumper = nextUuid;
                return nextUuid;
            }
        }

        currentJumper = null;
        return null;
    }

    // --- Wave Mode Support ---

    public void recordWaveJump(@NotNull UUID playerUuid) {
        waveJumpedPlayers.add(playerUuid);
    }

    public boolean isWaveComplete() {
        List<UUID> alive = getAlivePlayers();
        return waveJumpedPlayers.containsAll(alive);
    }

    public void resetWave() {
        waveJumpedPlayers.clear();
    }

    // --- Team Shared Pool Support ---

    public void setTeamLives(@NotNull UUID teamUuid, int lives) {
        teamLives.put(teamUuid, Math.max(0, lives));
    }

    public int getTeamLives(@NotNull UUID teamUuid) {
        return teamLives.getOrDefault(teamUuid, arena.getInitialLives());
    }

    public boolean decrementTeamLife(@NotNull UUID teamUuid) {
        int current = getTeamLives(teamUuid);
        if (current > 0) {
            int updated = current - 1;
            teamLives.put(teamUuid, updated);
            if (updated <= 0) {
                // Eliminate all team members
                for (DacPlayerSession session : participants.values()) {
                    if (session.getEffectiveHolderUuid().equals(teamUuid)) {
                        session.setSpectator(true);
                        session.setLives(0);
                    }
                }
            }
            return true;
        }
        return false;
    }

    public boolean incrementTeamLife(@NotNull UUID teamUuid, int maxLives) {
        int current = getTeamLives(teamUuid);
        if (current < maxLives) {
            teamLives.put(teamUuid, current + 1);
            return true;
        }
        return false;
    }
}
