package fr.danakube.danaevent.core.command;

import fr.danakube.danaevent.DanaEventPlugin;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Objects;

/**
 * Core admin subcommand that reloads config, messages and all registered modules.
 */
public class ReloadCommand implements SubCommand {

    private final DanaEventPlugin plugin;

    public ReloadCommand(DanaEventPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public List<String> getAliases() {
        return List.of("rl");
    }

    @Override
    public String getDescription() {
        return "Recharge les configurations et les modules";
    }

    @Override
    public String getSyntax() {
        return "/danaevent reload";
    }

    @Override
    public String getPermission() {
        return "danaevent.admin.reload";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        plugin.reloadConfig();
        plugin.getMessageManager().reload();
        plugin.getModuleManager().reloadAll();
        plugin.getMessageManager().sendMessage(sender, "reload-success");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
