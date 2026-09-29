package fr.danakube.danaevent.modules.boatrace.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.modules.boatrace.BoatRaceModule;
import fr.danakube.danaevent.modules.boatrace.gui.BoatRaceLeaderboardGui;
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
 * Player subcommand: /de br top <circuit> [alltime|monthly]
 * Opens the interactive leaderboard GUI for the track.
 */
public class BoatRaceTopCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final BoatRaceModule module;

    public BoatRaceTopCmd(DanaEventPlugin plugin, BoatRaceModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "top";
    }

    @Override
    public List<String> getAliases() {
        return List.of("leaderboard", "lb");
    }

    @Override
    public String getDescription() {
        return "Affiche le classement d'un circuit";
    }

    @Override
    public String getSyntax() {
        return "/de br top <circuit> [alltime|monthly]";
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

        String trackId = args[0];
        Optional<Track> trackOpt = module.getTrackManager().getTrack(trackId);
        if (trackOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(player, "boatrace-track-not-found", Placeholder.parsed("track", trackId));
            return;
        }

        BoatRaceLeaderboardGui.Scope scope = BoatRaceLeaderboardGui.Scope.MONTHLY;
        if (args.length >= 2) {
            String scopeArg = args[1].toLowerCase();
            if (scopeArg.equals("alltime") || scopeArg.equals("all")) {
                scope = BoatRaceLeaderboardGui.Scope.ALL_TIME;
            } else if (scopeArg.equals("monthly") || scopeArg.equals("month")) {
                scope = BoatRaceLeaderboardGui.Scope.MONTHLY;
            }
        }

        BoatRaceLeaderboardGui gui = new BoatRaceLeaderboardGui(plugin, module.getLeaderboardManager(), trackOpt.get().getId(), scope);
        gui.open(player);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> trackIds = module.getTrackManager().getTracks().stream().map(Track::getId).toList();
            List<String> matched = StringUtil.copyPartialMatches(args[0], trackIds, new ArrayList<>());
            Collections.sort(matched);
            return matched;
        } else if (args.length == 2) {
            List<String> scopes = List.of("alltime", "monthly");
            List<String> matched = StringUtil.copyPartialMatches(args[1], scopes, new ArrayList<>());
            Collections.sort(matched);
            return matched;
        }
        return List.of();
    }
}
