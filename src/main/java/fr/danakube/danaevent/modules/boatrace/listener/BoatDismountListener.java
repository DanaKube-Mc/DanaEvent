package fr.danakube.danaevent.modules.boatrace.listener;

import fr.danakube.danaevent.modules.boatrace.manager.RaceManager;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleExitEvent;

import java.util.Objects;

/**
 * Listens for vehicle dismount events to enforce strict Anti-Cut mechanics during races.
 */
public class BoatDismountListener implements Listener {

    private final RaceManager raceManager;

    public BoatDismountListener(RaceManager raceManager) {
        this.raceManager = Objects.requireNonNull(raceManager, "raceManager cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player player && event.getVehicle() instanceof Boat boat) {
            if (raceManager.isRacing(player.getUniqueId())) {
                raceManager.handleDismount(player, boat);
            }
        }
    }
}
