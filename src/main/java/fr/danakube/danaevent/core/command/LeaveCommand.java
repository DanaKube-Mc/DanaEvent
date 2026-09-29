package fr.danakube.danaevent.core.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.modules.boatrace.BoatRaceModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Command allowing players to leave an ongoing event or race.
 * Accessible via both '/leave' and '/danaevent leave'.
 */
public class LeaveCommand implements SubCommand, CommandExecutor, TabCompleter {

    private final DanaEventPlugin plugin;

    public LeaveCommand(DanaEventPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
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
        return "Quitter l'événement ou la course en cours";
    }

    @Override
    public String getSyntax() {
        return "/danaevent leave";
    }

    @Override
    public String getPermission() {
        return null; // Publicly accessible to any player participating in an event
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

        executeLeave(player);
    }

    /**
     * Executes the leave logic for the specified player.
     *
     * @param player the player attempting to leave
     * @return true if player was in an event and left, false otherwise
     */
    public boolean executeLeave(Player player) {
        // 1. Check BoatRace module
        Optional<BoatRaceModule> boatRace = plugin.getModuleManager()
            .getModule("boatrace", BoatRaceModule.class);
        if (boatRace.isPresent() && boatRace.get().getRaceManager().isRacing(player.getUniqueId())) {
            boatRace.get().getRaceManager().cancelRace(player, "Abandon");
            return true;
        }

        // 2. Check general player state backup (fallback for any other module)
        if (plugin.getPlayerStateManager().hasState(player.getUniqueId())) {
            plugin.getPlayerStateManager().restore(player, true);
            plugin.getMessageManager().sendMessage(player, "event-leave-success");
            return true;
        }

        // 3. Not in any active event
        plugin.getMessageManager().sendMessage(player, "event-not-in-event");
        return false;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        execute(sender, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
