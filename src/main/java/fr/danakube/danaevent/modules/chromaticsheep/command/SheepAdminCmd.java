package fr.danakube.danaevent.modules.chromaticsheep.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.chromaticsheep.ChromaticSheepModule;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameFormat;
import fr.danakube.danaevent.modules.chromaticsheep.model.ScoringMode;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepArena;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Subcommand dispatcher: /de mc admin ...
 * Admin commands for ChromaticSheep arena creation, configuration, match management and leaderboard reset.
 */
public class SheepAdminCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final ChromaticSheepModule module;

    public SheepAdminCmd(DanaEventPlugin plugin, ChromaticSheepModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "admin";
    }

    @Override
    public List<String> getAliases() {
        return List.of("manage");
    }

    @Override
    public String getDescription() {
        return "Commandes d'administration de Mouton Chromatique";
    }

    @Override
    public String getSyntax() {
        return "/de mc admin <arena|resetranking> ...";
    }

    @Override
    public String getPermission() {
        return "danaevent.chromaticsheep.admin";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
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

        if (args[1].equalsIgnoreCase("create")) {
            handleArenaCreate(sender, args);
            return;
        }

        String arenaId = args[1].trim().toLowerCase();
        Optional<SheepArena> arenaOpt = module.getArenaManager().getArena(arenaId);
        if (arenaOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(sender, "sheep-not-found", Placeholder.parsed("arena", arenaId));
            return;
        }

        SheepArena arena = arenaOpt.get();
        if (args.length < 3) {
            sendHelp(sender);
            return;
        }

        String action = args[2].toLowerCase();
        switch (action) {
            case "setbounds" -> handleSetBounds(sender, arena);
            case "addplayerspawn" -> handleAddPlayerSpawn(sender, arena);
            case "setspawnsheep" -> handleSetSpawnSheep(sender, arena, args);
            case "setduration" -> handleSetDuration(sender, arena, args);
            case "start" -> handleStart(sender, arena);
            case "stop" -> handleStop(sender, arena);
            default -> sendHelp(sender);
        }
    }

    private void handleArenaCreate(CommandSender sender, String[] args) {
        // Syntax: /de mc admin arena create <id> <SOLO|TEAM> <FINAL_COUNT|DOMINATION|ACTION>
        if (args.length < 5) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Syntaxe : /de mc admin arena create <id> <SOLO|TEAM> <FINAL_COUNT|DOMINATION|ACTION></red>");
            return;
        }

        String id = args[2].trim().toLowerCase();
        if (!id.matches("^[a-z0-9_-]{3,32}$")) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>L'ID doit être composé de 3 à 32 caractères alphanumériques.</red>");
            return;
        }

        if (module.getArenaManager().getArena(id).isPresent()) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Une arène avec cet ID existe déjà.</red>");
            return;
        }

        GameFormat format;
        try {
            format = GameFormat.valueOf(args[3].toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getMessageManager().sendMessage(sender, "sheep-admin-invalid-format");
            return;
        }

        ScoringMode mode;
        try {
            mode = ScoringMode.fromString(args[4]);
        } catch (IllegalArgumentException e) {
            plugin.getMessageManager().sendMessage(sender, "sheep-admin-invalid-mode");
            return;
        }

        SheepArena arena = module.getArenaManager().createArena(id, id, format, mode);
        module.getArenaManager().saveArenas();

        plugin.getMessageManager().sendMessage(
            sender,
            "sheep-admin-arena-created",
            Placeholder.parsed("arena", arena.getId()),
            Placeholder.parsed("format", format.name()),
            Placeholder.parsed("mode", mode.name())
        );
    }

    private void handleSetBounds(CommandSender sender, SheepArena arena) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendMessage(sender, "player-only");
            return;
        }

        Optional<CuboidRegion> regionOpt = plugin.getSelectionManager().getRegion(player.getUniqueId());
        if (regionOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(player, "sheep-admin-no-selection");
            return;
        }

        arena.setBounds(regionOpt.get());
        module.getArenaManager().saveArenas();

        plugin.getMessageManager().sendMessage(
            player,
            "sheep-admin-bounds-set",
            Placeholder.parsed("arena", arena.getId())
        );
    }

    private void handleAddPlayerSpawn(CommandSender sender, SheepArena arena) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendMessage(sender, "player-only");
            return;
        }

        arena.addPlayerSpawn(player.getLocation());
        module.getArenaManager().saveArenas();

        plugin.getMessageManager().sendMessage(
            player,
            "sheep-admin-spawn-added",
            Placeholder.parsed("arena", arena.getId()),
            Placeholder.parsed("count", String.valueOf(arena.getPlayerSpawns().size()))
        );
    }

    private void handleSetSpawnSheep(CommandSender sender, SheepArena arena, String[] args) {
        if (args.length < 4) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Syntaxe : /de mc admin arena <id> setspawnsheep <nombre></red>");
            return;
        }

        int count;
        try {
            count = Integer.parseInt(args[3]);
            if (count <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendMessage(sender, "sheep-admin-invalid-number");
            return;
        }

        arena.setSheepCount(count);
        module.getArenaManager().saveArenas();

        plugin.getMessageManager().sendMessage(
            sender,
            "sheep-admin-spawnsheep-set",
            Placeholder.parsed("arena", arena.getId()),
            Placeholder.parsed("count", String.valueOf(count))
        );
    }

    private void handleSetDuration(CommandSender sender, SheepArena arena, String[] args) {
        if (args.length < 4) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Syntaxe : /de mc admin arena <id> setduration <secondes></red>");
            return;
        }

        int duration;
        try {
            duration = Integer.parseInt(args[3]);
            if (duration <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendMessage(sender, "sheep-admin-invalid-number");
            return;
        }

        arena.setDurationSeconds(duration);
        module.getArenaManager().saveArenas();

        plugin.getMessageManager().sendMessage(
            sender,
            "sheep-admin-duration-set",
            Placeholder.parsed("arena", arena.getId()),
            Placeholder.parsed("duration", String.valueOf(duration))
        );
    }

    private void handleStart(CommandSender sender, SheepArena arena) {
        if (!arena.isReady()) {
            plugin.getMessageManager().sendMessage(sender, "sheep-not-configured", Placeholder.parsed("arena", arena.getId()));
            return;
        }

        boolean started = module.getGameManager().startGame(arena.getId());
        if (started) {
            plugin.getMessageManager().sendMessage(sender, "sheep-admin-game-started", Placeholder.parsed("arena", arena.getId()));
        } else {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Impossible de lancer la partie : aucun joueur inscrit dans l'arène ou erreur de configuration.</red>");
        }
    }

    private void handleStop(CommandSender sender, SheepArena arena) {
        module.getGameManager().stopGame(arena.getId());
        plugin.getMessageManager().sendMessage(sender, "sheep-admin-game-stopped", Placeholder.parsed("arena", arena.getId()));
    }

    private void handleResetRanking(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Syntaxe : /de mc admin resetranking <arène> [YYYY-MM]</red>");
            return;
        }

        String arenaId = args[1].trim().toLowerCase();
        Optional<SheepArena> arenaOpt = module.getArenaManager().getArena(arenaId);
        if (arenaOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(sender, "sheep-not-found", Placeholder.parsed("arena", arenaId));
            return;
        }

        String period = args.length >= 3 ? args[2].trim() : null;

        module.getLeaderboardManager().resetRanking(arenaId, period).thenAccept(deleted -> {
            plugin.getMessageManager().sendMessage(
                sender,
                "sheep-admin-ranking-reset",
                Placeholder.parsed("arena", arenaId),
                Placeholder.parsed("count", String.valueOf(deleted))
            );
        });
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessageManager().sendRawMessage(sender, "<prefix> <gold><b>Commandes Admin Mouton Chromatique :</b></gold>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de mc admin arena create <id> <SOLO|TEAM> <FINAL_COUNT|DOMINATION|ACTION></yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de mc admin arena <id> setbounds</yellow> <gray>(sélection Wand)</gray>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de mc admin arena <id> addplayerspawn</yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de mc admin arena <id> setspawnsheep <nombre></yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de mc admin arena <id> setduration <secondes></yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de mc admin arena <id> start</yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de mc admin arena <id> stop</yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de mc admin resetranking <id> [YYYY-MM]</yellow>");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("arena", "resetranking"), args[0]);
        }

        if (args[0].equalsIgnoreCase("arena")) {
            if (args.length == 2) {
                List<String> list = new ArrayList<>();
                list.add("create");
                list.addAll(module.getArenaManager().getArenas().stream().map(SheepArena::getId).toList());
                return filterPrefix(list, args[1]);
            }

            if (args[1].equalsIgnoreCase("create")) {
                if (args.length == 4) {
                    return filterPrefix(List.of("SOLO", "TEAM"), args[3]);
                }
                if (args.length == 5) {
                    return filterPrefix(List.of("FINAL_COUNT", "DOMINATION_TICK", "ACTION_SCORE"), args[4]);
                }
            } else {
                if (args.length == 3) {
                    return filterPrefix(List.of("setbounds", "addplayerspawn", "setspawnsheep", "setduration", "start", "stop"), args[2]);
                }
            }
        }

        if (args[0].equalsIgnoreCase("resetranking") && args.length == 2) {
            return filterPrefix(module.getArenaManager().getArenas().stream().map(SheepArena::getId).toList(), args[1]);
        }

        return Collections.emptyList();
    }

    private List<String> filterPrefix(List<String> source, String prefix) {
        String lower = prefix.toLowerCase();
        return source.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
    }
}
