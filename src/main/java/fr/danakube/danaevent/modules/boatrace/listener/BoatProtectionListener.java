package fr.danakube.danaevent.modules.boatrace.listener;

import fr.danakube.danaevent.modules.boatrace.manager.RaceManager;
import org.bukkit.entity.Boat;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

import java.util.Objects;

/**
 * Listens for vehicle damage and destruction events to guarantee invulnerability of racing boats.
 */
public class BoatProtectionListener implements Listener {

    private final RaceManager raceManager;

    public BoatProtectionListener(RaceManager raceManager) {
        this.raceManager = Objects.requireNonNull(raceManager, "raceManager cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (event.getVehicle() instanceof Boat && raceManager.isBoatInRace(event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (event.getVehicle() instanceof Boat && raceManager.isBoatInRace(event.getVehicle())) {
            event.setCancelled(true);
        }
    }
}
