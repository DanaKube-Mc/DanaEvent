package fr.danakube.danaevent.modules.deacoudre.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.modules.deacoudre.DeACoudreModule;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Subcommand: /de dac join <arena>
 */
public class DacJoinCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final DeACoudreModule module;

    public DacJoinCmd(DanaEventPlugin plugin, DeACoudreModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "join";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public String getDescription() {
        return "Rejoindre une partie de Dé à Coudre";
    }

    @Override
    public String getSyntax() {
        return "/de dac join <arène>";
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

        if (args.length < 1) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Utilisation : " + getSyntax() + "</red>");
            return;
        }

        String arenaId = args[0].trim().toLowerCase();
        Optional<DacArena> arenaOpt = module.getArenaManager().getArena(arenaId);
        if (arenaOpt.isEmpty()) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>L'arène '" + arenaId + "' n'existe pas.</red>");
            return;
        }

        DacArena arena = arenaOpt.get();
        boolean joined = module.getGameManager().joinGame(player, arena.getId());
        if (joined) {
            plugin.getMessageManager().sendMessage(
                player,
                "dac-join-success",
                Placeholder.parsed("arena", arena.getDisplayName())
            );
        } else {
            plugin.getMessageManager().sendMessage(player, "dac-join-fail");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            String prefix = args[0].toLowerCase();
            for (DacArena arena : module.getArenaManager().getArenas()) {
                if (arena.getId().toLowerCase().startsWith(prefix)) {
                    suggestions.add(arena.getId());
                }
            }
            return suggestions;
        }
        return List.of();
    }
}
