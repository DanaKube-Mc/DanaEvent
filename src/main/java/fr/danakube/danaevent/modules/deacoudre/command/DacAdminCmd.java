package fr.danakube.danaevent.modules.deacoudre.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.deacoudre.DeACoudreModule;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameFormat;
import fr.danakube.danaevent.modules.deacoudre.model.JumpMode;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Administrative commands for Dé à Coudre arena configuration and game control.
 */
public class DacAdminCmd implements SubCommand {

    public static final String PERMISSION = "danaevent.deacoudre.admin";

    private final DanaEventPlugin plugin;
    private final DeACoudreModule module;

    public DacAdminCmd(DanaEventPlugin plugin, DeACoudreModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "admin";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public String getDescription() {
        return "Administration du module Dé à Coudre";
    }

    @Override
    public String getSyntax() {
        return "/de dac admin <arena|resetranking> ...";
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
        if (!sender.hasPermission(PERMISSION)) {
            plugin.getMessageManager().sendMessage(sender, "no-permission");
            return;
        }

        if (args.length < 1) {
            sendHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "arena" -> handleArena(sender, args);
            case "resetranking" -> handleResetRanking(sender, args);
            default -> sendHelp(sender);
        }
    }

    private void handleArena(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }

        String action = args[1].toLowerCase();

        // /de dac admin arena create <id> <SOLO|TEAM> <TURN_BY_TURN|WAVE>
        if ("create".equalsIgnoreCase(action)) {
            if (args.length < 3) {
                plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Utilisation : /de dac admin arena create <id> [SOLO|TEAM] [TURN_BY_TURN|WAVE]</red>");
                return;
            }

            String id = args[2].trim().toLowerCase();
            if (module.getArenaManager().getArena(id).isPresent()) {
                plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Une arène avec l'identifiant '" + id + "' existe déjà.</red>");
                return;
            }

            DacGameFormat format = DacGameFormat.SOLO;
            if (args.length >= 4) {
                try {
                    format = DacGameFormat.fromString(args[3]);
                } catch (Exception e) {
                    plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Format invalide (SOLO ou TEAM).</red>");
                    return;
                }
            }

            JumpMode mode = JumpMode.TURN_BY_TURN;
            if (args.length >= 5) {
                try {
                    mode = JumpMode.fromString(args[4]);
                } catch (Exception e) {
                    plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Mode invalide (TURN_BY_TURN ou WAVE).</red>");
                    return;
                }
            }

            DacArena created = module.getArenaManager().createArena(id, id, format, mode);
            module.getArenaManager().saveArena(created);

            plugin.getMessageManager().sendMessage(
                sender,
                "dac-admin-created",
                Placeholder.parsed("arena", created.getId())
            );
            return;
        }

        // Other arena subcommands: /de dac admin arena <id> <subaction> [params]
        String arenaId = args[1].trim().toLowerCase();
        Optional<DacArena> arenaOpt = module.getArenaManager().getArena(arenaId);
        if (arenaOpt.isEmpty()) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>L'arène '" + arenaId + "' n'existe pas.</red>");
            return;
        }

        DacArena arena = arenaOpt.get();
        if (args.length < 3) {
            sendHelp(sender);
            return;
        }

        String subAction = args[2].toLowerCase();
        switch (subAction) {
            case "setpool" -> {
                if (!(sender instanceof Player player)) {
                    plugin.getMessageManager().sendMessage(sender, "player-only");
                    return;
                }
                Optional<CuboidRegion> regionOpt = plugin.getSelectionManager().getRegion(player.getUniqueId());
                if (regionOpt.isEmpty()) {
                    plugin.getMessageManager().sendRawMessage(player, "<prefix> <red>Veuillez d'abord sélectionner le bassin avec la Wand (/de wand) !</red>");
                    return;
                }
                arena.setPoolRegion(regionOpt.get());
                module.getArenaManager().saveArena(arena);
                plugin.getMessageManager().sendMessage(player, "dac-admin-pool-set");
            }
            case "setdiving" -> {
                if (!(sender instanceof Player player)) {
                    plugin.getMessageManager().sendMessage(sender, "player-only");
                    return;
                }
                arena.setDivingLocation(player.getLocation());
                module.getArenaManager().saveArena(arena);
                plugin.getMessageManager().sendMessage(player, "dac-admin-diving-set");
            }
            case "setlobby" -> {
                if (!(sender instanceof Player player)) {
                    plugin.getMessageManager().sendMessage(sender, "player-only");
                    return;
                }
                arena.setLobbyLocation(player.getLocation());
                module.getArenaManager().saveArena(arena);
                plugin.getMessageManager().sendMessage(player, "dac-admin-lobby-set");
            }
            case "setlives" -> {
                if (args.length < 4) {
                    plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Utilisation : /de dac admin arena " + arenaId + " setlives <nombre></red>");
                    return;
                }
                try {
                    int lives = Integer.parseInt(args[3]);
                    arena.setInitialLives(lives);
                    module.getArenaManager().saveArena(arena);
                    plugin.getMessageManager().sendMessage(
                        sender,
                        "dac-admin-lives-set",
                        Placeholder.parsed("lives", String.valueOf(arena.getInitialLives()))
                    );
                } catch (NumberFormatException e) {
                    plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Nombre de vies invalide.</red>");
                }
            }
            case "setjumptime" -> {
                if (args.length < 4) {
                    plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Utilisation : /de dac admin arena " + arenaId + " setjumptime <secondes></red>");
                    return;
                }
                try {
                    int time = Integer.parseInt(args[3]);
                    arena.setJumpTimeSeconds(time);
                    module.getArenaManager().saveArena(arena);
                    plugin.getMessageManager().sendMessage(
                        sender,
                        "dac-admin-jumptime-set",
                        Placeholder.parsed("time", String.valueOf(arena.getJumpTimeSeconds()))
                    );
                } catch (NumberFormatException e) {
                    plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Temps de saut invalide.</red>");
                }
            }
            case "start" -> {
                boolean started = module.getGameManager().startGame(arena.getId());
                if (started) {
                    plugin.getMessageManager().sendMessage(
                        sender,
                        "dac-admin-start-success",
                        Placeholder.parsed("arena", arena.getDisplayName())
                    );
                } else {
                    plugin.getMessageManager().sendMessage(sender, "dac-admin-start-fail");
                }
            }
            case "stop" -> {
                module.getGameManager().stopGame(arena.getId());
                plugin.getMessageManager().sendMessage(
                    sender,
                    "dac-admin-stop-success",
                    Placeholder.parsed("arena", arena.getDisplayName())
                );
            }
            default -> sendHelp(sender);
        }
    }

    private void handleResetRanking(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Utilisation : /de dac admin resetranking <arène> [YYYY-MM]</red>");
            return;
        }

        String arenaId = args[1].trim().toLowerCase();
        String period = args.length >= 3 ? args[2].trim() : null;

        module.getLeaderboardManager().resetRanking(arenaId, period).thenAccept(count -> {
            plugin.getMessageManager().sendMessage(
                sender,
                "dac-admin-reset-ranking",
                Placeholder.parsed("arena", arenaId)
            );
        });
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessageManager().sendRawMessage(sender, "<prefix> <gradient:#00c6ff:#0072ff><b>Commandes Admin Dé à Coudre :</b></gradient>");
        plugin.getMessageManager().sendRawMessage(sender, "<gray>• /de dac admin arena create <id> [SOLO|TEAM] [TURN_BY_TURN|WAVE]</gray>");
        plugin.getMessageManager().sendRawMessage(sender, "<gray>• /de dac admin arena <id> setpool (sélection Wand)</gray>");
        plugin.getMessageManager().sendRawMessage(sender, "<gray>• /de dac admin arena <id> setdiving</gray>");
        plugin.getMessageManager().sendRawMessage(sender, "<gray>• /de dac admin arena <id> setlobby</gray>");
        plugin.getMessageManager().sendRawMessage(sender, "<gray>• /de dac admin arena <id> setlives <nombre></gray>");
        plugin.getMessageManager().sendRawMessage(sender, "<gray>• /de dac admin arena <id> setjumptime <secondes></gray>");
        plugin.getMessageManager().sendRawMessage(sender, "<gray>• /de dac admin arena <id> start</gray>");
        plugin.getMessageManager().sendRawMessage(sender, "<gray>• /de dac admin arena <id> stop</gray>");
        plugin.getMessageManager().sendRawMessage(sender, "<gray>• /de dac admin resetranking <id> [YYYY-MM]</gray>");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(List.of("arena", "resetranking"), args[0]);
        }

        if (args.length == 2 && "arena".equalsIgnoreCase(args[0])) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("create");
            for (DacArena arena : module.getArenaManager().getArenas()) {
                suggestions.add(arena.getId());
            }
            return filter(suggestions, args[1]);
        }

        if (args.length == 3 && "arena".equalsIgnoreCase(args[0])) {
            if ("create".equalsIgnoreCase(args[1])) {
                return List.of("<id>");
            }
            return filter(List.of("setpool", "setdiving", "setlobby", "setlives", "setjumptime", "start", "stop"), args[2]);
        }

        if (args.length == 4 && "arena".equalsIgnoreCase(args[0]) && "create".equalsIgnoreCase(args[1])) {
            return filter(List.of("SOLO", "TEAM"), args[3]);
        }

        if (args.length == 5 && "arena".equalsIgnoreCase(args[0]) && "create".equalsIgnoreCase(args[1])) {
            return filter(List.of("TURN_BY_TURN", "WAVE"), args[4]);
        }

        if (args.length == 2 && "resetranking".equalsIgnoreCase(args[0])) {
            List<String> arenas = new ArrayList<>();
            for (DacArena arena : module.getArenaManager().getArenas()) {
                arenas.add(arena.getId());
            }
            return filter(arenas, args[1]);
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> matched = new ArrayList<>();
        String lower = prefix.toLowerCase();
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(lower)) {
                matched.add(opt);
            }
        }
        return matched;
    }
}
