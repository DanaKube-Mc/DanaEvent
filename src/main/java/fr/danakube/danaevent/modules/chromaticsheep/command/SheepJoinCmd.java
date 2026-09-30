package fr.danakube.danaevent.modules.chromaticsheep.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.modules.chromaticsheep.ChromaticSheepModule;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameFormat;
import fr.danakube.danaevent.modules.chromaticsheep.model.GameState;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepArena;
import fr.danakube.danaevent.modules.chromaticsheep.model.SheepGame;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Subcommand: /de mc join <arène>
 * Joins a ChromaticSheep arena match.
 */
public class SheepJoinCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final ChromaticSheepModule module;

    public SheepJoinCmd(DanaEventPlugin plugin, ChromaticSheepModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "join";
    }

    @Override
    public List<String> getAliases() {
        return List.of("play");
    }

    @Override
    public String getDescription() {
        return "Rejoindre une arène de Mouton Chromatique";
    }

    @Override
    public String getSyntax() {
        return "/de mc join <arène>";
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
            plugin.getMessageManager().sendRawMessage(player, "<prefix> <red>Syntaxe : " + getSyntax() + "</red>");
            return;
        }

        if (plugin.getPlayerStateManager().hasSnapshot(player.getUniqueId()) ||
            module.getGameManager().getSession(player.getUniqueId()).isPresent()) {
            plugin.getMessageManager().sendMessage(player, "sheep-already-in-game");
            return;
        }

        String arenaId = args[0].trim().toLowerCase();
        Optional<SheepArena> arenaOpt = module.getArenaManager().getArena(arenaId);
        if (arenaOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(player, "sheep-not-found", Placeholder.parsed("arena", arenaId));
            return;
        }

        SheepArena arena = arenaOpt.get();
        if (!arena.isEnabled() || !arena.isReady()) {
            plugin.getMessageManager().sendMessage(player, "sheep-not-configured", Placeholder.parsed("arena", arena.getId()));
            return;
        }

        SheepGame game = module.getGameManager().getGame(arena.getId());
        if (game != null && game.getState() == GameState.RUNNING) {
            plugin.getMessageManager().sendMessage(player, "sheep-game-already-running");
            return;
        }

        if (arena.getFormat() == GameFormat.TEAM && plugin.getTeamManager() != null) {
            if (plugin.getTeamManager().getPlayerTeam(player.getUniqueId()).isEmpty()) {
                plugin.getMessageManager().sendRawMessage(player, "<prefix> <red>Cette arène se joue en équipe. Rejoignez ou créez une équipe d'abord !</red>");
                return;
            }
        }

        boolean success = module.getGameManager().joinGame(player, arena.getId());
        if (!success) {
            plugin.getMessageManager().sendRawMessage(player, "<prefix> <red>Impossible de rejoindre la partie (arène pleine ou déjà en cours).</red>");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return module.getArenaManager().getArenas().stream()
                .filter(SheepArena::isEnabled)
                .map(SheepArena::getId)
                .filter(id -> id.toLowerCase().startsWith(prefix))
                .toList();
        }
        return Collections.emptyList();
    }
}
