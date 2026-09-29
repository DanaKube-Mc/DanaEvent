package fr.danakube.danaevent.modules.treasurehunt.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.modules.treasurehunt.TreasureHuntModule;
import fr.danakube.danaevent.modules.treasurehunt.display.QuestJournalMenu;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Subcommand: /de hunt journal
 * Opens the interactive Quest Journal GUI displaying all unlocked clues.
 */
public class HuntJournalCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final TreasureHuntModule module;

    public HuntJournalCmd(DanaEventPlugin plugin, TreasureHuntModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "journal";
    }

    @Override
    public List<String> getAliases() {
        return List.of("book", "clues");
    }

    @Override
    public String getDescription() {
        return "Ouvrir votre journal de quête";
    }

    @Override
    public String getSyntax() {
        return "/de hunt journal";
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
        Player player = (Player) sender;
        Optional<PlayerHuntProgress> progressOpt = module.getProgressManager().getProgressForPlayer(player);

        if (progressOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(player, "hunt-not-active");
            return;
        }

        PlayerHuntProgress progress = progressOpt.get();
        Optional<Hunt> huntOpt = module.getHuntConfig().getHunt(progress.getHuntId());
        if (huntOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(player, "hunt-not-found");
            return;
        }

        plugin.getGuiManager().openGui(player, new QuestJournalMenu(huntOpt.get(), progress));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
