package fr.danakube.danaevent.modules.deacoudre.manager;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Callback interface invoked when a jumper lands in water or fails their jump.
 */
public interface DacJumpCallback {

    void onJumpSuccess(@NotNull Player player, @NotNull Block waterBlock, boolean isPerfect);

    void onJumpFail(@NotNull Player player, @NotNull String reason);
}
