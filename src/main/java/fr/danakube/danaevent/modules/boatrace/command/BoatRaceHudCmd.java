package fr.danakube.danaevent.modules.boatrace.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.modules.boatrace.BoatRaceModule;
import fr.danakube.danaevent.modules.boatrace.model.HudType;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * Player subcommand: /de br hud [bossbar|actionbar|both]
 * Switches or queries the preferred chrono display HUD mode.
 */
public class BoatRaceHudCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final BoatRaceModule module;

    public BoatRaceHudCmd(DanaEventPlugin plugin, BoatRaceModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "hud";
    }

    @Override
    public List<String> getAliases() {
        return List.of("bossbar", "actionbar", "chrono");
    }

    @Override
    public String getDescription() {
        return "Changer le mode d'affichage du chrono (BossBar, Action Bar ou les deux)";
    }

    @Override
    public String getSyntax() {
        return "/de br hud [bossbar|actionbar|both]";
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

        if (args.length == 0) {
            HudType current = module.getRaceManager().getPlayerHudPreference(player.getUniqueId());
            plugin.getMessageManager().sendMessage(
                player,
                "boatrace-hud-current",
                Placeholder.parsed("hud_type", current.getDisplayName())
            );
            return;
        }

        HudType selected = HudType.fromString(args[0]);
        if (selected == null) {
            plugin.getMessageManager().sendMessage(player, "boatrace-hud-invalid");
            return;
        }

        module.getRaceManager().setPlayerHudPreference(player.getUniqueId(), selected);
        plugin.getMessageManager().sendMessage(
            player,
            "boatrace-hud-updated",
            Placeholder.parsed("hud_type", selected.getDisplayName())
        );
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return List.of("bossbar", "actionbar", "both").stream()
                .filter(opt -> opt.startsWith(prefix))
                .toList();
        }
        return List.of();
    }
}
