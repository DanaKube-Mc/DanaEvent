package fr.danakube.danaevent.modules.boatrace.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.modules.boatrace.BoatRaceModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * Player subcommand: /de br leave
 * Leaves/cancels the current boat race session.
 */
public class BoatRaceLeaveCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final BoatRaceModule module;

    public BoatRaceLeaveCmd(DanaEventPlugin plugin, BoatRaceModule module) {
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
        return "Quitter la course de bateaux en cours";
    }

    @Override
    public String getSyntax() {
        return "/de br leave";
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

        if (!module.getRaceManager().isRacing(player.getUniqueId())) {
            plugin.getMessageManager().sendMessage(player, "boatrace-not-racing");
            return;
        }

        module.getRaceManager().cancelRace(player, "Abandon");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
