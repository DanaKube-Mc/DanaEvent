package fr.danakube.danaevent.core.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.module.DanaModule;
import fr.danakube.danaevent.core.module.ModuleStatus;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Core admin subcommand that lists all registered modules with their state (activé/désactivé/erreur).
 */
public class ModulesCommand implements SubCommand {

    private final DanaEventPlugin plugin;

    public ModulesCommand(DanaEventPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    public String getName() {
        return "modules";
    }

    @Override
    public List<String> getAliases() {
        return List.of("mods", "list");
    }

    @Override
    public String getDescription() {
        return "Liste les modules enregistrés avec leur état";
    }

    @Override
    public String getSyntax() {
        return "/danaevent modules";
    }

    @Override
    public String getPermission() {
        return "danaevent.admin.modules";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Collection<DanaModule> modules = plugin.getModuleManager().getModules();
        plugin.getMessageManager().sendRawMessage(sender, "<dark_gray>----------------------------------------</dark_gray>");
        plugin.getMessageManager().sendRawMessage(sender, "<prefix> <gray>Modules enregistrés (<white>" + modules.size() + "</white>) :</gray>");

        if (modules.isEmpty()) {
            plugin.getMessageManager().sendRawMessage(sender, "<gray>Aucun module enregistré.</gray>");
        } else {
            for (DanaModule module : modules) {
                ModuleStatus status = plugin.getModuleManager().getModuleStatus(module.getId());
                String statusDisplay = switch (status) {
                    case ENABLED -> "<green>Activé</green>";
                    case DISABLED -> "<gray>Désactivé</gray>";
                    case ERROR -> "<red>Erreur</red>";
                };
                plugin.getMessageManager().sendRawMessage(
                    sender,
                    "<gray>- </gray><aqua>" + module.getName() + "</aqua> <dark_gray>(" + module.getId() + " v" + module.getVersion() + ")</dark_gray> : " + statusDisplay
                );
            }
        }

        plugin.getMessageManager().sendRawMessage(sender, "<dark_gray>----------------------------------------</dark_gray>");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
