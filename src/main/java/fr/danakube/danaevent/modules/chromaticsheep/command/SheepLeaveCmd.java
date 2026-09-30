package fr.danakube.danaevent.modules.chromaticsheep.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.modules.chromaticsheep.ChromaticSheepModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Subcommand: /de mc leave
 * Leaves an ongoing ChromaticSheep game.
 */
public class SheepLeaveCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final ChromaticSheepModule module;

    public SheepLeaveCmd(DanaEventPlugin plugin, ChromaticSheepModule module) {
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
        return "Quitter la partie de Mouton Chromatique en cours";
    }

    @Override
    public String getSyntax() {
        return "/de mc leave";
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

        boolean left = module.getGameManager().leaveGame(player);
        if (!left) {
            plugin.getMessageManager().sendMessage(player, "sheep-not-in-game");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
