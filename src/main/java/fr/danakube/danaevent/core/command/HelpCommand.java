package fr.danakube.danaevent.core.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.module.DanaModule;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Objects;

/**
 * Core admin subcommand that displays available commands and subcommands allowed for the sender.
 */
public class HelpCommand implements SubCommand {

    private final DanaEventPlugin plugin;

    public HelpCommand(DanaEventPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public List<String> getAliases() {
        return List.of("h", "?");
    }

    @Override
    public String getDescription() {
        return "Affiche la liste des commandes autorisées";
    }

    @Override
    public String getSyntax() {
        return "/danaevent help";
    }

    @Override
    public String getPermission() {
        return "danaevent.admin.help";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        plugin.getMessageManager().sendRawMessage(sender, "<dark_gray>----------------------------------------</dark_gray>");
        plugin.getMessageManager().sendRawMessage(sender, "<prefix> <gray>Commandes autorisées :</gray>");

        // Core commands
        for (SubCommand cmd : plugin.getCommandManager().getCoreCommands().values()) {
            if (cmd.getPermission() == null || cmd.getPermission().isEmpty() || sender.hasPermission(cmd.getPermission())) {
                plugin.getMessageManager().sendRawMessage(
                    sender,
                    "<yellow>" + cmd.getSyntax() + "</yellow> <dark_gray>-</dark_gray> <gray>" + cmd.getDescription() + "</gray>"
                );
            }
        }

        // Active module commands
        for (DanaModule module : plugin.getModuleManager().getModules()) {
            if (module.isEnabled()) {
                for (SubCommand cmd : module.getSubCommands()) {
                    if (cmd.getPermission() == null || cmd.getPermission().isEmpty() || sender.hasPermission(cmd.getPermission())) {
                        plugin.getMessageManager().sendRawMessage(
                            sender,
                            "<yellow>" + cmd.getSyntax() + "</yellow> <dark_gray>-</dark_gray> <gray>" + cmd.getDescription() + "</gray>"
                        );
                    }
                }
            }
        }

        plugin.getMessageManager().sendRawMessage(sender, "<dark_gray>----------------------------------------</dark_gray>");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
