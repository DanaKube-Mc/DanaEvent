package fr.danakube.danaevent.modules.deacoudre.model;

import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Snapshot of a block inside the Dé à Coudre pool to allow clean, instant rollback.
 */
public record DacPoolBlock(
    int x,
    int y,
    int z,
    @NotNull BlockData originalData
) {
    public DacPoolBlock {
        Objects.requireNonNull(originalData, "originalData cannot be null");
    }
}
