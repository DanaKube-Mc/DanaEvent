package fr.danakube.danaevent.modules.chromaticsheep.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.modules.chromaticsheep.ChromaticSheepModule;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepArena;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepRecord;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Subcommand: /de mc top <arène> [alltime|monthly]
 * Displays top scores for a ChromaticSheep arena.
 */
public class SheepTopCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final ChromaticSheepModule module;

    public SheepTopCmd(DanaEventPlugin plugin, ChromaticSheepModule module) {
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
        return "Afficher le classement des meilleurs scores d'une arène";
    }

    @Override
    public String getSyntax() {
        return "/de mc top <arène> [alltime|monthly]";
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
        if (args.length < 1) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Syntaxe : " + getSyntax() + "</red>");
            return;
        }

        String arenaId = args[0].trim().toLowerCase();
        Optional<SheepArena> arenaOpt = module.getArenaManager().getArena(arenaId);
        if (arenaOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(sender, "sheep-not-found", Placeholder.parsed("arena", arenaId));
            return;
        }

        SheepArena arena = arenaOpt.get();
        boolean isAllTime = args.length >= 2 && args[1].equalsIgnoreCase("alltime");
        String periodLabel = isAllTime ? "Tous les temps" : "Mensuel";

        var future = isAllTime
            ? module.getLeaderboardManager().getTopAllTime(arenaId, 10)
            : module.getLeaderboardManager().getTopMonthly(arenaId, 10);

        future.thenAccept(records -> {
            if (records.isEmpty()) {
                plugin.getMessageManager().sendMessage(sender, "sheep-top-empty");
                return;
            }

            plugin.getMessageManager().sendMessage(
                sender,
                "sheep-top-header",
                Placeholder.parsed("arena", arena.getDisplayName()),
                Placeholder.parsed("period", periodLabel)
            );

            for (int i = 0; i < records.size(); i++) {
                SheepRecord rec = records.get(i);
                String holderName = module.getLeaderboardManager().resolveHolderName(rec.holderUuid(), rec.isTeam());

                plugin.getMessageManager().sendMessage(
                    sender,
                    "sheep-top-entry",
                    Placeholder.parsed("rank", String.valueOf(i + 1)),
                    Placeholder.parsed("holder", holderName),
                    Placeholder.parsed("score", String.valueOf(rec.scorePoints())),
                    Placeholder.parsed("sheep", String.valueOf(rec.sheepCount()))
                );
            }
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return module.getArenaManager().getArenas().stream()
                .map(SheepArena::getId)
                .filter(id -> id.toLowerCase().startsWith(prefix))
                .toList();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return List.of("alltime", "monthly").stream()
                .filter(s -> s.startsWith(prefix))
                .toList();
        }
        return Collections.emptyList();
    }
}
