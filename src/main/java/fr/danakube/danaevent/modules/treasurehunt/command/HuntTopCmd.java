package fr.danakube.danaevent.modules.treasurehunt.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.modules.treasurehunt.TreasureHuntModule;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntRecord;
import fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Subcommand: /de hunt top <chasse> [alltime|monthly]
 * Displays top completion speed records for a treasure hunt.
 */
public class HuntTopCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final TreasureHuntModule module;

    public HuntTopCmd(DanaEventPlugin plugin, TreasureHuntModule module) {
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
        return "Afficher le classement des meilleurs temps d'une chasse";
    }

    @Override
    public String getSyntax() {
        return "/de hunt top <chasse> [alltime|monthly]";
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

        String huntId = args[0].trim().toLowerCase();
        Optional<Hunt> huntOpt = module.getHuntConfig().getHunt(huntId);
        if (huntOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(sender, "hunt-not-found", Placeholder.parsed("hunt", huntId));
            return;
        }

        Hunt hunt = huntOpt.get();
        boolean isAllTime = args.length >= 2 && args[1].equalsIgnoreCase("alltime");
        String periodLabel = isAllTime ? "Tous les temps" : "Mensuel";

        var future = isAllTime
            ? module.getLeaderboardManager().getTopAllTime(huntId, 10)
            : module.getLeaderboardManager().getTopMonthly(huntId, 10);

        future.thenAccept(records -> {
            if (records.isEmpty()) {
                plugin.getMessageManager().sendMessage(sender, "hunt-top-empty");
                return;
            }

            plugin.getMessageManager().sendMessage(
                sender,
                "hunt-top-header",
                Placeholder.parsed("hunt", hunt.getDisplayName()),
                Placeholder.parsed("period", periodLabel)
            );

            for (int i = 0; i < records.size(); i++) {
                HuntRecord rec = records.get(i);
                String holderName = module.getLeaderboardManager().resolveHolderName(rec.holderUuid(), rec.isTeam());
                String formattedTime = PlayerHuntProgress.formatTime(rec.timeMillis());

                plugin.getMessageManager().sendMessage(
                    sender,
                    "hunt-top-entry",
                    Placeholder.parsed("rank", String.valueOf(i + 1)),
                    Placeholder.parsed("holder", holderName),
                    Placeholder.parsed("time", formattedTime)
                );
            }
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return module.getHuntConfig().getHunts().stream()
                .map(Hunt::getId)
                .filter(id -> id.startsWith(args[0].toLowerCase()))
                .toList();
        }
        if (args.length == 2) {
            return List.of("alltime", "monthly").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}
