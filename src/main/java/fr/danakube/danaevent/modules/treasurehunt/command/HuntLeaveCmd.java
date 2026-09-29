package fr.danakube.danaevent.modules.treasurehunt.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.modules.treasurehunt.TreasureHuntModule;
import fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Subcommand: /de hunt leave
 * Leaves or cancels the current active treasure hunt.
 */
public class HuntLeaveCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final TreasureHuntModule module;

    public HuntLeaveCmd(DanaEventPlugin plugin, TreasureHuntModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "leave";
    }

    @Override
    public List<String> getAliases() {
        return List.of("quit", "abandon");
    }

    @Override
    public String getDescription() {
        return "Abandonner la chasse au trésor en cours";
    }

    @Override
    public String getSyntax() {
        return "/de hunt leave";
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
        Optional<PlayerHuntProgress> progressOpt = module.getProgressManager().getProgressForPlayer(player);

        if (progressOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(player, "hunt-not-active");
            return;
        }

        PlayerHuntProgress progress = progressOpt.get();

        if (progress.isTeam()) {
            if (plugin.getTeamManager() != null) {
                Optional<DanaTeam> teamOpt = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
                if (teamOpt.isPresent()) {
                    DanaTeam team = teamOpt.get();
                    if (!team.isLeader(player.getUniqueId())) {
                        plugin.getMessageManager().sendRawMessage(
                            player,
                            "<prefix> <red>Seul le chef d'équipe peut abandonner la chasse d'équipe.</red>"
                        );
                        return;
                    }

                    module.getProgressManager().cancelHunt(progress.getHolderUuid());
                    for (UUID memberUuid : team.getMembers().keySet()) {
                        Player member = Bukkit.getPlayer(memberUuid);
                        if (member != null && member.isOnline()) {
                            plugin.getMessageManager().sendMessage(member, "hunt-cancelled");
                        }
                    }
                    return;
                }
            }
        }

        module.getProgressManager().cancelHunt(progress.getHolderUuid());
        plugin.getMessageManager().sendMessage(player, "hunt-cancelled");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
