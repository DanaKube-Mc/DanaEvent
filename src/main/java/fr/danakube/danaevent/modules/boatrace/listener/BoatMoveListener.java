package fr.danakube.danaevent.modules.boatrace.listener;

import fr.danakube.danaevent.modules.boatrace.manager.RaceManager;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;

import java.util.Objects;

/**
 * Listens for vehicle movement events to trigger line crossing detection for active racers.
 */
public class BoatMoveListener implements Listener {

    private final RaceManager raceManager;

    public BoatMoveListener(RaceManager raceManager) {
        this.raceManager = Objects.requireNonNull(raceManager, "raceManager cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (event.getVehicle() instanceof Boat boat) {
            for (Entity passenger : boat.getPassengers()) {
                if (passenger instanceof Player player && raceManager.isRacing(player.getUniqueId())) {
                    raceManager.handleMove(player, event.getFrom(), event.getTo());
                    break;
                }
            }
        }
    }
}
