package fr.danakube.danaevent.modules.treasurehunt.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.modules.treasurehunt.TreasureHuntModule;
import fr.danakube.danaevent.modules.treasurehunt.manager.HuntProgressManager;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntMode;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Subcommand: /de hunt join <chasse>
 * Joins or starts an active treasure hunt in solo or team mode.
 */
public class HuntJoinCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final TreasureHuntModule module;

    public HuntJoinCmd(DanaEventPlugin plugin, TreasureHuntModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "join";
    }

    @Override
    public List<String> getAliases() {
        return List.of("play", "start");
    }

    @Override
    public String getDescription() {
        return "Rejoindre ou lancer une chasse au trésor";
    }

    @Override
    public String getSyntax() {
        return "/de hunt join <chasse>";
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
        Player player = (Player) sender;

        if (module.getProgressManager().isParticipant(player)) {
            plugin.getMessageManager().sendMessage(player, "hunt-already-participating");
            return;
        }

        if (args.length < 1) {
            plugin.getMessageManager().sendRawMessage(player, "<prefix> <red>Syntaxe : " + getSyntax() + "</red>");
            return;
        }

        String huntId = args[0].trim().toLowerCase();
        Optional<Hunt> huntOpt = module.getHuntConfig().getHunt(huntId);
        if (huntOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(player, "hunt-not-found", Placeholder.parsed("hunt", huntId));
            return;
        }

        Hunt hunt = huntOpt.get();
        if (!hunt.isEnabled() || !hunt.isReady()) {
            plugin.getMessageManager().sendMessage(player, "hunt-not-ready");
            return;
        }

        if (hunt.getMode() == HuntMode.TEAM) {
            if (plugin.getTeamManager() == null) {
                plugin.getMessageManager().sendMessage(player, "hunt-team-required");
                return;
            }

            Optional<DanaTeam> teamOpt = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
            if (teamOpt.isEmpty()) {
                plugin.getMessageManager().sendMessage(player, "hunt-team-required");
                return;
            }

            DanaTeam team = teamOpt.get();
            UUID teamUuid = HuntProgressManager.getTeamUuid(team.getId());
            if (module.getProgressManager().isParticipant(teamUuid)) {
                plugin.getMessageManager().sendMessage(player, "hunt-already-participating");
                return;
            }

            module.getProgressManager().startHunt(teamUuid, true, hunt.getId());

            for (UUID memberUuid : team.getMembers().keySet()) {
                Player member = Bukkit.getPlayer(memberUuid);
                if (member != null && member.isOnline()) {
                    plugin.getMessageManager().sendMessage(
                        member,
                        "hunt-team-started",
                        Placeholder.parsed("hunt", hunt.getDisplayName())
                    );
                }
            }
        } else {
            module.getProgressManager().startHunt(player.getUniqueId(), false, hunt.getId());
            plugin.getMessageManager().sendMessage(
                player,
                "hunt-solo-started",
                Placeholder.parsed("hunt", hunt.getDisplayName())
            );
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return module.getHuntConfig().getHunts().stream()
                .filter(Hunt::isEnabled)
                .map(Hunt::getId)
                .filter(id -> id.startsWith(args[0].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}
