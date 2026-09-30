package fr.danakube.danaevent.modules.deacoudre.manager;

import fr.danakube.danaevent.modules.deacoudre.config.DacConfig;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameFormat;
import fr.danakube.danaevent.modules.deacoudre.model.JumpMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Service manager for managing Dé à Coudre arenas.
 */
public class DacArenaManager {

    private final DacConfig dacConfig;

    public DacArenaManager(@NotNull DacConfig dacConfig) {
        this.dacConfig = Objects.requireNonNull(dacConfig, "dacConfig cannot be null");
    }

    public @NotNull Collection<DacArena> getArenas() {
        return dacConfig.getArenas();
    }

    public Optional<DacArena> getArena(@Nullable String id) {
        return dacConfig.getArena(id);
    }

    public DacArena createArena(
        @NotNull String id,
        @NotNull String displayName,
        @NotNull DacGameFormat format,
        @NotNull JumpMode jumpMode
    ) {
        return dacConfig.createArena(id, displayName, format, jumpMode);
    }

    public boolean deleteArena(@Nullable String id) {
        return dacConfig.deleteArena(id);
    }

    public void registerArena(@NotNull DacArena arena) {
        dacConfig.registerArena(arena);
    }

    public void saveArena(@NotNull DacArena arena) {
        dacConfig.registerArena(arena);
        dacConfig.saveArenas();
    }

    public void saveArenas() {
        dacConfig.saveArenas();
    }

    public void loadArenas() {
        dacConfig.loadArenas();
    }

    public @NotNull DacConfig getConfig() {
        return dacConfig;
    }
}
