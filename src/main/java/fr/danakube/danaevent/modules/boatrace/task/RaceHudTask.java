package fr.danakube.danaevent.modules.boatrace.task;

import fr.danakube.danaevent.modules.boatrace.manager.RaceManager;
import fr.danakube.danaevent.modules.boatrace.model.RaceSession;
import fr.danakube.danaevent.modules.boatrace.model.RaceState;
import fr.danakube.danaevent.modules.boatrace.model.TrackType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Objects;

/**
 * Periodically updates the ActionBar HUD for all active racers with current chrono and lap.
 */
public class RaceHudTask extends BukkitRunnable {

    private final RaceManager raceManager;
    private final MiniMessage miniMessage;

    public RaceHudTask(RaceManager raceManager) {
        this.raceManager = Objects.requireNonNull(raceManager, "raceManager cannot be null");
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public void run() {
        for (RaceSession session : raceManager.getActiveSessions().values()) {
            if (session.getState() != RaceState.RACING) {
                continue;
            }

            Player player = Bukkit.getPlayer(session.getPlayerUuid());
            if (player == null || !player.isOnline()) {
                continue;
            }

            long elapsed = session.getElapsedTimeMillis();
            String formattedTime = RaceSession.formatTime(elapsed);
            int currentLap = session.getCurrentLap();
            int totalLaps = session.getTrack().getLaps();

            String hudText;
            if (session.getTrack().getType() == TrackType.SPRINT) {
                hudText = "<gradient:#00c6ff:#0072ff><b>Chrono :</b></gradient> <yellow>"
                    + formattedTime + "</yellow> <dark_gray>|</dark_gray> <gray>Sprint</gray>";
            } else {
                hudText = "<gradient:#00c6ff:#0072ff><b>Chrono :</b></gradient> <yellow>"
                    + formattedTime + "</yellow> <dark_gray>|</dark_gray> <gray>Tour : <aqua>["
                    + currentLap + "/" + totalLaps + "]</aqua></gray>";
            }

            Component component = miniMessage.deserialize(hudText);
            player.sendActionBar(component);
        }
    }
}
