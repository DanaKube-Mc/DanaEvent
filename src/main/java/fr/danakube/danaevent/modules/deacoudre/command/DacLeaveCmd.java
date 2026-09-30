package fr.danakube.danaevent.modules.deacoudre.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.modules.deacoudre.DeACoudreModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * Subcommand: /de dac leave
 */
public class DacLeaveCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final DeACoudreModule module;

    public DacLeaveCmd(DanaEventPlugin plugin, DeACoudreModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "leave";
    }

    @Override
    public List<String> getAliases() {
        return List.of("quit");
    }

    @Override
    public String getDescription() {
        return "Quitter la partie de Dé à Coudre en cours";
    }

    @Override
    public String getSyntax() {
        return "/de dac leave";
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

        if (module.getGameManager().getSession(player.getUniqueId()).isEmpty()) {
            plugin.getMessageManager().sendMessage(player, "dac-not-in-game");
            return;
        }

        module.getGameManager().leaveGame(player);
        plugin.getMessageManager().sendMessage(player, "dac-leave-success");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
