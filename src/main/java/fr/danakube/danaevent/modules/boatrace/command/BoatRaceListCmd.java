package fr.danakube.danaevent.modules.boatrace.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.modules.boatrace.BoatRaceModule;
import fr.danakube.danaevent.modules.boatrace.model.Track;
import fr.danakube.danaevent.modules.boatrace.model.TrackMode;
import fr.danakube.danaevent.modules.boatrace.model.TrackType;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Player subcommand: /de br list
 * Lists all existing tracks with characteristics (mode, laps, status).
 */
public class BoatRaceListCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final BoatRaceModule module;

    public BoatRaceListCmd(DanaEventPlugin plugin, BoatRaceModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public List<String> getAliases() {
        return List.of("tracks");
    }

    @Override
    public String getDescription() {
        return "Liste les circuits de course de bateaux";
    }

    @Override
    public String getSyntax() {
        return "/de br list";
    }

    @Override
    public String getPermission() {
        return null;
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Collection<Track> tracks = module.getTrackManager().getTracks();

        if (tracks.isEmpty()) {
            plugin.getMessageManager().sendMessage(sender, "boatrace-no-tracks");
            return;
        }

        plugin.getMessageManager().sendRawMessage(sender, "<dark_gray>----------------------------------------</dark_gray>");
        plugin.getMessageManager().sendRawMessage(sender, "<prefix> <gray>Circuits disponibles (<white>" + tracks.size() + "</white>) :</gray>");

        for (Track track : tracks) {
            String typeStr = track.getType() == TrackType.SPRINT ? "Sprint" : track.getLaps() + " tour" + (track.getLaps() > 1 ? "s" : "");
            String modeStr = track.getMode() == TrackMode.TIME_ATTACK_247 ? "<green>24/7</green>" : "<gold>Event</gold>";
            String readyStr = track.isReady() ? "<green>Prêt</green>" : "<red>Incomplet</red>";

            plugin.getMessageManager().sendRawMessage(
                sender,
                "<gray>- </gray><yellow><b>" + track.getId() + "</b></yellow> <dark_gray>(</dark_gray><aqua>" + track.getName() + "</aqua><dark_gray>)</dark_gray> <gray>[" + typeStr + " | " + modeStr + " | " + readyStr + "]</gray>"
            );
        }

        plugin.getMessageManager().sendRawMessage(sender, "<dark_gray>----------------------------------------</dark_gray>");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
