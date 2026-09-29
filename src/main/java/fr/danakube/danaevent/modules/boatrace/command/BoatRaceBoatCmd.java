package fr.danakube.danaevent.modules.boatrace.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.modules.boatrace.BoatRaceModule;
import fr.danakube.danaevent.modules.boatrace.gui.BoatSelectionGui;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Player subcommand: /de br boat
 * Opens the boat cosmetic selection GUI.
 */
public class BoatRaceBoatCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final BoatRaceModule module;

    public BoatRaceBoatCmd(DanaEventPlugin plugin, BoatRaceModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "boat";
    }

    @Override
    public List<String> getAliases() {
        return List.of("boats", "skin", "skins", "vestiaire");
    }

    @Override
    public String getDescription() {
        return "Choisir votre modèle de bateau favori";
    }

    @Override
    public String getSyntax() {
        return "/de br boat";
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

        if (module.getRaceManager().isRacing(player.getUniqueId())) {
            plugin.getMessageManager().sendMessage(player, "boatrace-already-racing");
            return;
        }

        BoatSelectionGui gui = new BoatSelectionGui(plugin, module.getBoatSkinManager());
        gui.open(player);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
