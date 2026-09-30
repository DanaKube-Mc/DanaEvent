package fr.danakube.danaevent.modules.chromaticsheep.task;

import fr.danakube.danaevent.modules.chromaticsheep.manager.SheepGameManager;
import fr.danakube.danaevent.modules.chromaticsheep.manager.SheepScoreManager;
import fr.danakube.danaevent.modules.chromaticsheep.model.PlayerSheepSession;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Periodically sends live ActionBar HUD updates (paint bomb cooldown and player score) to active participants.
 */
public class HudUpdateTask implements Runnable {

    private final SheepGameManager gameManager;
    private final SheepScoreManager scoreManager;
    private final String arenaId;

    public HudUpdateTask(
        @NotNull SheepGameManager gameManager,
        @NotNull SheepScoreManager scoreManager,
        @NotNull String arenaId
    ) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager cannot be null");
        this.scoreManager = Objects.requireNonNull(scoreManager, "scoreManager cannot be null");
        this.arenaId = Objects.requireNonNull(arenaId, "arenaId cannot be null");
    }

    @Override
    public void run() {
        SheepGame game = gameManager.getGame(arenaId);
        if (game == null) {
            return;
        }

        for (PlayerSheepSession session : game.getSessions().values()) {
            Player player = Bukkit.getPlayer(session.getPlayerUuid());
            if (player != null && player.isOnline()) {
                Component actionBar = buildActionBar(session);
                player.sendActionBar(actionBar);
            }
        }
    }

    public @NotNull Component buildActionBar(@NotNull PlayerSheepSession session) {
        String bombText;
        if (session.canThrowBomb()) {
            bombText = "<gradient:#ff007f:#7928ca><b>Bombe</b></gradient> : <green><b>PRÊTE</b></green>";
        } else {
            double remaining = session.getRemainingBombCooldownSeconds();
            bombText = "<gradient:#ff007f:#7928ca><b>Bombe</b></gradient> : <yellow>" + remaining + "s</yellow>";
        }

        int score = scoreManager.getScore(arenaId, session.getEffectiveHolderUuid());
        String barString = "<dark_gray>[</dark_gray>" + bombText + "<dark_gray>]</dark_gray> <dark_gray>|</dark_gray> <white>Score : <yellow><b>" + score + "</b></yellow> pts</white>";

        return MiniMessage.miniMessage().deserialize(barString);
    }
}
