package fr.danakube.danaevent.modules.chromaticsheep.manager;

import fr.danakube.danaevent.modules.chromaticsheep.config.ArenaConfig;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameFormat;
import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepArena;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Service manager for managing ChromaticSheep arenas.
 */
public class ArenaManager {

    private final ArenaConfig arenaConfig;

    public ArenaManager(@NotNull ArenaConfig arenaConfig) {
        this.arenaConfig = Objects.requireNonNull(arenaConfig, "arenaConfig cannot be null");
    }

    public @NotNull Collection<SheepArena> getArenas() {
        return arenaConfig.getArenas();
    }

    public Optional<SheepArena> getArena(@Nullable String id) {
        return arenaConfig.getArena(id);
    }

    public SheepArena createArena(
        @NotNull String id,
        @NotNull String displayName,
        @NotNull GameFormat format,
        @NotNull ScoringMode scoringMode
    ) {
        return arenaConfig.createArena(id, displayName, format, scoringMode);
    }

    public boolean deleteArena(@Nullable String id) {
        return arenaConfig.deleteArena(id);
    }

    public void registerArena(@NotNull SheepArena arena) {
        arenaConfig.registerArena(arena);
    }

    public void saveArenas() {
        arenaConfig.saveArenas();
    }

    public void loadArenas() {
        arenaConfig.loadArenas();
    }

    public @NotNull ArenaConfig getConfig() {
        return arenaConfig;
    }
}
