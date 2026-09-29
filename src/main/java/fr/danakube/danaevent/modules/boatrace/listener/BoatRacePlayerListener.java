package fr.danakube.danaevent.modules.boatrace.listener;

import fr.danakube.danaevent.modules.boatrace.manager.BoatSkinManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Listens to player events to preload boat race preferences.
 */
public class BoatRacePlayerListener implements Listener {

    private final BoatSkinManager boatSkinManager;

    public BoatRacePlayerListener(@NotNull BoatSkinManager boatSkinManager) {
        this.boatSkinManager = Objects.requireNonNull(boatSkinManager, "boatSkinManager cannot be null");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event != null && event.getPlayer() != null) {
            boatSkinManager.loadPlayerPreference(event.getPlayer().getUniqueId());
        }
    }
}
