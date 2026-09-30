package fr.danakube.danaevent.modules.chromaticsheep.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.modules.chromaticsheep.ChromaticSheepModule;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameState;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepArena;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepGame;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Subcommand: /de mc list
 * Lists all configured ChromaticSheep arenas.
 */
public class SheepListCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final ChromaticSheepModule module;

    public SheepListCmd(DanaEventPlugin plugin, ChromaticSheepModule module) {
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
        return "Lister les arènes de Mouton Chromatique";
    }

    @Override
    public String getSyntax() {
        return "/de mc list";
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
        Collection<SheepArena> arenas = module.getArenaManager().getArenas();
        if (arenas.isEmpty()) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <gray>Aucune arène n'est actuellement configurée.</gray>");
            return;
        }

        plugin.getMessageManager().sendMessage(sender, "sheep-list-header");

        for (SheepArena arena : arenas) {
            String status;
            SheepGame game = module.getGameManager().getGame(arena.getId());
            if (game != null && game.getState() == GameState.RUNNING) {
                status = "<red>En cours (" + game.getSessions().size() + "j)</red>";
            } else if (game != null && (game.getState() == GameState.WAITING || game.getState() == GameState.COUNTDOWN)) {
                status = "<yellow>En attente (" + game.getSessions().size() + "j)</yellow>";
            } else if (!arena.isEnabled()) {
                status = "<red>Désactivée</red>";
            } else if (!arena.isReady()) {
                status = "<red>Non configurée</red>";
            } else {
                status = "<green>Disponible</green>";
            }

            plugin.getMessageManager().sendMessage(
                sender,
                "sheep-list-entry",
                Placeholder.parsed("arena", arena.getDisplayName()),
                Placeholder.parsed("format", arena.getFormat().name()),
                Placeholder.parsed("mode", arena.getScoringMode().name()),
                Placeholder.parsed("sheep", String.valueOf(arena.getSheepCount())),
                Placeholder.parsed("status", status)
            );
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
