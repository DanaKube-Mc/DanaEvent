package fr.danakube.danaevent.modules.deacoudre.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.modules.deacoudre.DeACoudreModule;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacRecord;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Subcommand: /de dac top <arène> [alltime|monthly]
 */
public class DacTopCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final DeACoudreModule module;

    public DacTopCmd(DanaEventPlugin plugin, DeACoudreModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "top";
    }

    @Override
    public List<String> getAliases() {
        return List.of("leaderboard");
    }

    @Override
    public String getDescription() {
        return "Afficher le classement d'une arène de Dé à Coudre";
    }

    @Override
    public String getSyntax() {
        return "/de dac top <arène> [alltime|monthly]";
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
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Utilisation : " + getSyntax() + "</red>");
            return;
        }

        String arenaId = args[0].trim().toLowerCase();
        Optional<DacArena> arenaOpt = module.getArenaManager().getArena(arenaId);
        if (arenaOpt.isEmpty()) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>L'arène '" + arenaId + "' n'existe pas.</red>");
            return;
        }

        DacArena arena = arenaOpt.get();
        boolean allTime = args.length > 1 && "alltime".equalsIgnoreCase(args[1]);

        var queryFuture = allTime
            ? module.getLeaderboardManager().getTopAllTime(arena.getId(), 10)
            : module.getLeaderboardManager().getTopMonthly(arena.getId(), 10);

        queryFuture.thenAccept(records -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (records.isEmpty()) {
                    plugin.getMessageManager().sendMessage(sender, "dac-top-empty");
                    return;
                }

                plugin.getMessageManager().sendMessage(
                    sender,
                    "dac-top-header",
                    Placeholder.parsed("arena", arena.getDisplayName())
                );

                int rank = 1;
                for (DacRecord rec : records) {
                    String holderName = module.getLeaderboardManager().resolveHolderName(rec.holderUuid(), rec.isTeam());
                    plugin.getMessageManager().sendMessage(
                        sender,
                        "dac-top-entry",
                        Placeholder.parsed("rank", String.valueOf(rank)),
                        Placeholder.parsed("holder", holderName),
                        Placeholder.parsed("wins", String.valueOf(rec.wins())),
                        Placeholder.parsed("perfects", String.valueOf(rec.perfectDacs()))
                    );
                    rank++;
                }
            });
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            String prefix = args[0].toLowerCase();
            for (DacArena arena : module.getArenaManager().getArenas()) {
                if (arena.getId().toLowerCase().startsWith(prefix)) {
                    suggestions.add(arena.getId());
                }
            }
            return suggestions;
        }
        if (args.length == 2) {
            return List.of("monthly", "alltime");
        }
        return List.of();
    }
}
