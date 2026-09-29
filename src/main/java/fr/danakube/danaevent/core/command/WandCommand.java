package fr.danakube.danaevent.core.command;

import fr.danakube.danaevent.DanaEventPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * Core admin subcommand that gives the selection wand to the executing player.
 */
public class WandCommand implements SubCommand {

    private final DanaEventPlugin plugin;

    public WandCommand(DanaEventPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    public String getName() {
        return "wand";
    }

    @Override
    public List<String> getAliases() {
        return List.of("w");
    }

    @Override
    public String getDescription() {
        return "Donne le bâton de sélection";
    }

    @Override
    public String getSyntax() {
        return "/danaevent wand";
    }

    @Override
    public String getPermission() {
        return "danaevent.admin.wand";
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

        plugin.getSelectionManager().giveWand(player);
        plugin.getMessageManager().sendMessage(player, "wand-given");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
