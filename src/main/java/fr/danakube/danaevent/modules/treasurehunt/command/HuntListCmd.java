package fr.danakube.danaevent.modules.treasurehunt.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.modules.treasurehunt.TreasureHuntModule;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Subcommand: /de hunt list
 * Displays all configured treasure hunts and their attributes.
 */
public class HuntListCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final TreasureHuntModule module;

    public HuntListCmd(DanaEventPlugin plugin, TreasureHuntModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public List<String> getAliases() {
        return List.of("hunts");
    }

    @Override
    public String getDescription() {
        return "Liste les chasses au trésor disponibles";
    }

    @Override
    public String getSyntax() {
        return "/de hunt list";
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
        Collection<Hunt> hunts = module.getHuntConfig().getHunts();
        if (hunts.isEmpty()) {
            plugin.getMessageManager().sendMessage(sender, "hunt-not-found", Placeholder.parsed("hunt", "*"));
            return;
        }

        plugin.getMessageManager().sendMessage(sender, "hunt-list-header");
        for (Hunt hunt : hunts) {
            String status = hunt.isEnabled() ? "<green>Activé</green>" : "<red>Désactivé</red>";
            plugin.getMessageManager().sendMessage(
                sender,
                "hunt-list-entry",
                Placeholder.parsed("hunt", hunt.getId()),
                Placeholder.parsed("mode", hunt.getMode().name()),
                Placeholder.parsed("steps", String.valueOf(hunt.getStepCount())),
                Placeholder.parsed("status", status)
            );
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
