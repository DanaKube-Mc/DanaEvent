package fr.danakube.danaevent.modules.boatrace.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.boatrace.BoatRaceModule;
import fr.danakube.danaevent.modules.boatrace.model.Track;
import fr.danakube.danaevent.modules.boatrace.model.TrackMode;
import fr.danakube.danaevent.modules.boatrace.model.TrackType;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Admin subcommand: /de br admin ...
 * Requires permission 'danaevent.boatrace.admin'.
 */
public class BoatRaceAdminCmd implements SubCommand {

    public static final String PERMISSION = "danaevent.boatrace.admin";

    private final DanaEventPlugin plugin;
    private final BoatRaceModule module;

    public BoatRaceAdminCmd(DanaEventPlugin plugin, BoatRaceModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "admin";
    }

    @Override
    public List<String> getAliases() {
        return List.of("a");
    }

    @Override
    public String getDescription() {
        return "Administration des circuits de course de bateaux";
    }

    @Override
    public String getSyntax() {
        return "/de br admin <track|event|resetranking> ...";
    }

    @Override
    public String getPermission() {
        return PERMISSION;
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            displayAdminHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "track" -> handleTrack(sender, args);
            case "event" -> handleEvent(sender, args);
            case "resetranking" -> handleResetRanking(sender, args);
            default -> {
                plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Sous-commande admin inconnue : <yellow>" + args[0] + "</yellow>.</red>");
                displayAdminHelp(sender);
            }
        }
    }

    private void handleTrack(CommandSender sender, String[] args) {
        if (args.length < 2) {
            displayTrackHelp(sender);
            return;
        }

        if (args[1].equalsIgnoreCase("create")) {
            if (args.length < 5) {
                plugin.getMessageManager().sendRawMessage(sender, "<prefix> <gray>Utilisation : <yellow>/de br admin track create <id> <SPRINT|CIRCUIT_LAPS> <247|EVENT></yellow></gray>");
                return;
            }

            String id = args[2].toLowerCase();
            if (module.getTrackManager().getTrack(id).isPresent()) {
                plugin.getMessageManager().sendMessage(sender, "boatrace-admin-track-exists", Placeholder.parsed("track", id));
                return;
            }

            TrackType type;
            try {
                type = TrackType.valueOf(args[3].toUpperCase());
            } catch (IllegalArgumentException e) {
                if (args[3].equalsIgnoreCase("LAPS")) {
                    type = TrackType.CIRCUIT_LAPS;
                } else {
                    plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Type invalide : <yellow>" + args[3] + "</yellow>. Valeurs : SPRINT, CIRCUIT_LAPS</red>");
                    return;
                }
            }

            TrackMode mode;
            String modeArg = args[4].toUpperCase();
            if (modeArg.equals("247") || modeArg.equals("TIME_ATTACK_247")) {
                mode = TrackMode.TIME_ATTACK_247;
            } else if (modeArg.equals("EVENT") || modeArg.equals("EVENT_COMPETITION")) {
                mode = TrackMode.EVENT_COMPETITION;
            } else {
                plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Mode invalide : <yellow>" + args[4] + "</yellow>. Valeurs : 247, EVENT</red>");
                return;
            }

            Track track = module.getTrackManager().createTrack(id, id, type, mode);
            module.getTrackManager().saveTracks();

            plugin.getMessageManager().sendMessage(
                sender,
                "boatrace-admin-track-created",
                Placeholder.parsed("track", id),
                Placeholder.parsed("type", type.name()),
                Placeholder.parsed("mode", mode.name())
            );
            return;
        }

        // track <id> <action>
        String id = args[1].toLowerCase();
        Optional<Track> trackOpt = module.getTrackManager().getTrack(id);
        if (trackOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(sender, "boatrace-track-not-found", Placeholder.parsed("track", id));
            return;
        }

        Track track = trackOpt.get();
        if (args.length < 3) {
            displayTrackActionsHelp(sender, id);
            return;
        }

        String action = args[2].toLowerCase();
        switch (action) {
            case "setstart" -> {
                if (!(sender instanceof Player player)) {
                    plugin.getMessageManager().sendMessage(sender, "player-only");
                    return;
                }
                Optional<CuboidRegion> regionOpt = plugin.getSelectionManager().getRegion(player.getUniqueId());
                if (regionOpt.isEmpty()) {
                    plugin.getMessageManager().sendMessage(player, "boatrace-admin-no-selection");
                    return;
                }
                track.setStartRegion(regionOpt.get());
                module.getTrackManager().saveTracks();
                plugin.getMessageManager().sendMessage(player, "boatrace-admin-start-set", Placeholder.parsed("track", id));
            }
            case "setfinish" -> {
                if (!(sender instanceof Player player)) {
                    plugin.getMessageManager().sendMessage(sender, "player-only");
                    return;
                }
                Optional<CuboidRegion> regionOpt = plugin.getSelectionManager().getRegion(player.getUniqueId());
                if (regionOpt.isEmpty()) {
                    plugin.getMessageManager().sendMessage(player, "boatrace-admin-no-selection");
                    return;
                }
                track.setFinishRegion(regionOpt.get());
                module.getTrackManager().saveTracks();
                plugin.getMessageManager().sendMessage(player, "boatrace-admin-finish-set", Placeholder.parsed("track", id));
            }
            case "addspawn" -> {
                if (!(sender instanceof Player player)) {
                    plugin.getMessageManager().sendMessage(sender, "player-only");
                    return;
                }
                track.addSpawnPoint(player.getLocation());
                module.getTrackManager().saveTracks();
                plugin.getMessageManager().sendMessage(
                    player,
                    "boatrace-admin-spawn-added",
                    Placeholder.parsed("track", id),
                    Placeholder.parsed("count", String.valueOf(track.getSpawnPoints().size()))
                );
            }
            case "setlaps" -> {
                if (args.length < 4) {
                    plugin.getMessageManager().sendRawMessage(sender, "<prefix> <gray>Utilisation : <yellow>/de br admin track " + id + " setlaps <nombre></yellow></gray>");
                    return;
                }
                try {
                    int laps = Integer.parseInt(args[3]);
                    if (laps < 1) {
                        plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Le nombre de tours doit être d'au moins 1.</red>");
                        return;
                    }
                    track.setLaps(laps);
                    module.getTrackManager().saveTracks();
                    plugin.getMessageManager().sendMessage(sender, "boatrace-admin-laps-set", Placeholder.parsed("track", id), Placeholder.parsed("laps", String.valueOf(laps)));
                } catch (NumberFormatException e) {
                    plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Nombre de tours invalide : <yellow>" + args[3] + "</yellow></red>");
                }
            }
            case "togglecollision" -> {
                boolean newState = !track.isCollisionsEnabled();
                track.setCollisionsEnabled(newState);
                module.getTrackManager().saveTracks();
                String status = newState ? "<red>activées</red>" : "<green>désactivées</green>";
                plugin.getMessageManager().sendMessage(sender, "boatrace-admin-collision-toggled", Placeholder.parsed("track", id), Placeholder.parsed("status", status));
            }
            default -> {
                plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Action inconnue : <yellow>" + action + "</yellow>.</red>");
                displayTrackActionsHelp(sender, id);
            }
        }
    }

    private void handleEvent(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <gray>Utilisation : <yellow>/de br admin event <start|stop> <id></yellow></gray>");
            return;
        }

        String action = args[1].toLowerCase();
        String id = args[2].toLowerCase();

        Optional<Track> trackOpt = module.getTrackManager().getTrack(id);
        if (trackOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(sender, "boatrace-track-not-found", Placeholder.parsed("track", id));
            return;
        }

        if (action.equals("start")) {
            plugin.getMessageManager().sendMessage(sender, "boatrace-admin-event-started", Placeholder.parsed("track", id));
        } else if (action.equals("stop")) {
            for (var session : module.getRaceManager().getActiveSessions().values()) {
                if (session.getTrack().getId().equalsIgnoreCase(id)) {
                    Player p = Bukkit.getPlayer(session.getPlayerUuid());
                    if (p != null) {
                        module.getRaceManager().cancelRace(p, "Événement arrêté par un administrateur");
                    }
                }
            }
            plugin.getMessageManager().sendMessage(sender, "boatrace-admin-event-stopped", Placeholder.parsed("track", id));
        } else {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Action inconnue : <yellow>" + action + "</yellow>. Choix : start, stop</red>");
        }
    }

    private void handleResetRanking(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <gray>Utilisation : <yellow>/de br admin resetranking <id> [YYYY-MM]</yellow></gray>");
            return;
        }

        String id = args[1].toLowerCase();
        Optional<Track> trackOpt = module.getTrackManager().getTrack(id);
        if (trackOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(sender, "boatrace-track-not-found", Placeholder.parsed("track", id));
            return;
        }

        String periodMonth = args.length >= 3 ? args[2] : null;
        module.getLeaderboardManager().resetRanking(id, periodMonth).thenAccept(deleted -> {
            String periodDisplay = periodMonth != null ? periodMonth : "Tous les temps";
            plugin.getMessageManager().sendMessage(
                sender,
                "boatrace-admin-ranking-reset",
                Placeholder.parsed("track", id),
                Placeholder.parsed("period", periodDisplay),
                Placeholder.parsed("deleted", String.valueOf(deleted))
            );
        });
    }

    private void displayAdminHelp(CommandSender sender) {
        plugin.getMessageManager().sendRawMessage(sender, "<dark_gray>----------------------------------------</dark_gray>");
        plugin.getMessageManager().sendRawMessage(sender, "<prefix> <gray>Commandes Administrateur BoatRace :</gray>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de br admin track create <id> <SPRINT|CIRCUIT_LAPS> <247|EVENT></yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de br admin track <id> setstart</yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de br admin track <id> setfinish</yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de br admin track <id> addspawn</yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de br admin track <id> setlaps <nombre></yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de br admin track <id> togglecollision</yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de br admin event start <id></yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de br admin event stop <id></yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de br admin resetranking <id> [YYYY-MM]</yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<dark_gray>----------------------------------------</dark_gray>");
    }

    private void displayTrackHelp(CommandSender sender) {
        plugin.getMessageManager().sendRawMessage(sender, "<prefix> <gray>Sous-commandes de piste : <yellow>create</yellow> ou <yellow><id> <action></yellow></gray>");
    }

    private void displayTrackActionsHelp(CommandSender sender, String id) {
        plugin.getMessageManager().sendRawMessage(sender, "<prefix> <gray>Actions pour <yellow>" + id + "</yellow> : setstart, setfinish, addspawn, setlaps, togglecollision</gray>");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], List.of("track", "event", "resetranking"), new ArrayList<>());
        }

        if (args[0].equalsIgnoreCase("track")) {
            if (args.length == 2) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("create");
                for (Track t : module.getTrackManager().getTracks()) {
                    suggestions.add(t.getId());
                }
                return StringUtil.copyPartialMatches(args[1], suggestions, new ArrayList<>());
            }
            if (args[1].equalsIgnoreCase("create")) {
                if (args.length == 3) {
                    return List.of("<id>");
                }
                if (args.length == 4) {
                    return StringUtil.copyPartialMatches(args[3], List.of("SPRINT", "CIRCUIT_LAPS"), new ArrayList<>());
                }
                if (args.length == 5) {
                    return StringUtil.copyPartialMatches(args[4], List.of("247", "EVENT"), new ArrayList<>());
                }
                return List.of();
            } else {
                if (args.length == 3) {
                    return StringUtil.copyPartialMatches(args[2], List.of("setstart", "setfinish", "addspawn", "setlaps", "togglecollision"), new ArrayList<>());
                }
                if (args.length == 4 && args[2].equalsIgnoreCase("setlaps")) {
                    return StringUtil.copyPartialMatches(args[3], List.of("1", "2", "3", "5"), new ArrayList<>());
                }
                return List.of();
            }
        }

        if (args[0].equalsIgnoreCase("event")) {
            if (args.length == 2) {
                return StringUtil.copyPartialMatches(args[1], List.of("start", "stop"), new ArrayList<>());
            }
            if (args.length == 3) {
                List<String> trackIds = module.getTrackManager().getTracks().stream().map(Track::getId).toList();
                return StringUtil.copyPartialMatches(args[2], trackIds, new ArrayList<>());
            }
            return List.of();
        }

        if (args[0].equalsIgnoreCase("resetranking")) {
            if (args.length == 2) {
                List<String> trackIds = module.getTrackManager().getTracks().stream().map(Track::getId).toList();
                return StringUtil.copyPartialMatches(args[1], trackIds, new ArrayList<>());
            }
            if (args.length == 3) {
                return StringUtil.copyPartialMatches(args[2], List.of(module.getLeaderboardManager().getCurrentPeriodMonth(), "ALL"), new ArrayList<>());
            }
            return List.of();
        }

        return List.of();
    }
}
