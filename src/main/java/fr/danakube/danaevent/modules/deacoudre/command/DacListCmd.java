package fr.danakube.danaevent.modules.deacoudre.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.modules.deacoudre.DeACoudreModule;
import fr.danakube.danaevent.modules.deacoudre.model.DacArena;
import fr.danakube.danaevent.modules.deacoudre.model.DacGame;
import fr.danakube.danaevent.modules.deacoudre.model.DacGameState;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Subcommand: /de dac list
 */
public class DacListCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final DeACoudreModule module;

    public DacListCmd(DanaEventPlugin plugin, DeACoudreModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public List<String> getAliases() {
        return List.of("arenas");
    }

    @Override
    public String getDescription() {
        return "Lister les arènes de Dé à Coudre";
    }

    @Override
    public String getSyntax() {
        return "/de dac list";
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
        Collection<DacArena> arenas = module.getArenaManager().getArenas();
        if (arenas.isEmpty()) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <gray>Aucune arène de Dé à Coudre n'est configurée.</gray>");
            return;
        }

        plugin.getMessageManager().sendMessage(sender, "dac-list-header");

        for (DacArena arena : arenas) {
            String status;
            DacGame game = module.getGameManager().getGame(arena.getId());
            if (game != null && game.getState() == DacGameState.IN_GAME) {
                status = "<red>En cours (" + game.getParticipants().size() + "j)</red>";
            } else if (game != null && (game.getState() == DacGameState.WAITING || game.getState() == DacGameState.STARTING)) {
                status = "<yellow>En attente (" + game.getParticipants().size() + "j)</yellow>";
            } else if (!arena.isEnabled()) {
                status = "<red>Désactivée</red>";
            } else if (!arena.isReady()) {
                status = "<red>Non configurée</red>";
            } else {
                status = "<green>Disponible</green>";
            }

            plugin.getMessageManager().sendMessage(
                sender,
                "dac-list-entry",
                Placeholder.parsed("arena", arena.getDisplayName()),
                Placeholder.parsed("format", arena.getFormat().name()),
                Placeholder.parsed("mode", arena.getJumpMode().name()),
                Placeholder.parsed("status", status)
            );
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
