package fr.danakube.danaevent.modules.boatrace.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.modules.boatrace.BoatRaceModule;
import fr.danakube.danaevent.modules.boatrace.model.Track;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Player subcommand: /de br join <circuit>
 * Checks if already racing and starts the race session on the track.
 */
public class BoatRaceJoinCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final BoatRaceModule module;

    public BoatRaceJoinCmd(DanaEventPlugin plugin, BoatRaceModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "join";
    }

    @Override
    public List<String> getAliases() {
        return List.of("play");
    }

    @Override
    public String getDescription() {
        return "Rejoindre une course de bateaux sur un circuit";
    }

    @Override
    public String getSyntax() {
        return "/de br join <circuit>";
    }

    @Override
    public String getPermission() {
        return null;
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendMessage(sender, "player-only");
            return;
        }

        if (args.length < 1) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <gray>Utilisation : <yellow>" + getSyntax() + "</yellow></gray>");
            return;
        }

        if (module.getRaceManager().isRacing(player.getUniqueId())) {
            plugin.getMessageManager().sendMessage(player, "boatrace-already-racing");
            return;
        }

        String trackId = args[0];
        Optional<Track> trackOpt = module.getTrackManager().getTrack(trackId);
        if (trackOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(player, "boatrace-track-not-found", Placeholder.parsed("track", trackId));
            return;
        }

        Track track = trackOpt.get();
        if (!track.isReady()) {
            plugin.getMessageManager().sendMessage(player, "boatrace-track-not-ready", Placeholder.parsed("track", trackId));
            return;
        }

        boolean started = module.getRaceManager().startRace(player, track);
        if (!started) {
            plugin.getMessageManager().sendMessage(player, "error-generic");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> trackIds = module.getTrackManager().getTracks().stream().map(Track::getId).toList();
            List<String> matched = StringUtil.copyPartialMatches(args[0], trackIds, new ArrayList<>());
            Collections.sort(matched);
            return matched;
        }
        return List.of();
    }
}
