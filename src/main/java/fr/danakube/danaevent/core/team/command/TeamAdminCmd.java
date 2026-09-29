package fr.danakube.danaevent.core.team.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.core.team.manager.TeamManager;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Handles /de teamadmin subcommands for staff: force disbanding teams, force color assignment,
 * and manual points attribution.
 */
public class TeamAdminCmd implements SubCommand {

    public static final String PERMISSION = "danaevent.team.admin";

    private final DanaEventPlugin plugin;
    private final TeamManager teamManager;

    public TeamAdminCmd(@NotNull DanaEventPlugin plugin, @NotNull TeamManager teamManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.teamManager = Objects.requireNonNull(teamManager, "teamManager cannot be null");
    }

    @Override
    public @NotNull String getName() {
        return "teamadmin";
    }

    @Override
    public @NotNull List<String> getAliases() {
        return List.of("ta");
    }

    @Override
    public @NotNull String getDescription() {
        return "Administration des équipes événementielles";
    }

    @Override
    public @NotNull String getSyntax() {
        return "/de teamadmin <disband|setcolor|addpoints>";
    }

    @Override
    public @NotNull String getPermission() {
        return PERMISSION;
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            plugin.getMessageManager().sendMessage(sender, "no-permission");
            return;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "disband" -> handleDisband(sender, args);
            case "setcolor" -> handleSetColor(sender, args);
            case "addpoints" -> handleAddPoints(sender, args);
            default -> sendHelp(sender);
        }
    }

    private void handleDisband(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<prefix> <red>Utilisation : <yellow>/de teamadmin disband <nom_equipe></yellow></red>"
            ));
            return;
        }

        String teamId = args[1].toLowerCase(Locale.ROOT);
        DanaTeam team = teamManager.getTeam(teamId).orElse(null);
        if (team == null) {
            plugin.getMessageManager().sendMessage(sender, "team-not-found");
            return;
        }

        teamManager.disbandTeam(team.getId()).thenRun(() -> {
            plugin.getMessageManager().sendMessage(
                sender,
                "team-admin-disbanded",
                Placeholder.parsed("team", team.getFormattedName())
            );
        });
    }

    private void handleSetColor(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<prefix> <red>Utilisation : <yellow>/de teamadmin setcolor <équipe> <couleur></yellow></red>"
            ));
            return;
        }

        String teamId = args[1].toLowerCase(Locale.ROOT);
        DanaTeam team = teamManager.getTeam(teamId).orElse(null);
        if (team == null) {
            plugin.getMessageManager().sendMessage(sender, "team-not-found");
            return;
        }

        Optional<TeamColor> colorOpt = TeamColor.fromString(args[2]);
        if (colorOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(sender, "team-color-invalid");
            return;
        }

        TeamColor newColor = colorOpt.get();
        teamManager.changeTeamColor(team.getId(), newColor).thenAccept(success -> {
            if (success) {
                plugin.getMessageManager().sendMessage(
                    sender,
                    "team-admin-color-changed",
                    Placeholder.parsed("team", team.getFormattedName()),
                    Placeholder.parsed("color", newColor.getDisplayName())
                );
            } else {
                plugin.getMessageManager().sendMessage(
                    sender,
                    "team-color-taken",
                    Placeholder.parsed("color", newColor.getDisplayName())
                );
            }
        });
    }

    private void handleAddPoints(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<prefix> <red>Utilisation : <yellow>/de teamadmin addpoints <équipe> <event> <montant></yellow></red>"
            ));
            return;
        }

        String teamId = args[1].toLowerCase(Locale.ROOT);
        DanaTeam team = teamManager.getTeam(teamId).orElse(null);
        if (team == null) {
            plugin.getMessageManager().sendMessage(sender, "team-not-found");
            return;
        }

        String eventType = args[2].toLowerCase(Locale.ROOT);
        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendMessage(sender, "team-invalid-amount");
            return;
        }

        if (plugin.getTeamScoreManager() == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<prefix> <red>Le service de scoring n'est pas initialisé.</red>"
            ));
            return;
        }

        plugin.getTeamScoreManager().recordScore(team.getId(), eventType, amount).thenRun(() -> {
            plugin.getMessageManager().sendMessage(
                sender,
                "team-admin-points-added",
                Placeholder.parsed("amount", String.format(Locale.ROOT, "%.1f", amount)),
                Placeholder.parsed("team", team.getFormattedName()),
                Placeholder.parsed("event", eventType.toUpperCase(Locale.ROOT))
            );
        });
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
            "<dark_gray>▬▬▬▬▬▬▬▬</dark_gray> <gradient:#00c6ff:#0072ff><b>Administration Équipes</b></gradient> <dark_gray>▬▬▬▬▬▬▬▬</dark_gray>"
        ));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>/de teamadmin disband <équipe></yellow> <dark_gray>-</dark_gray> <gray>Dissoudre de force une équipe</gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>/de teamadmin setcolor <équipe> <couleur></yellow> <dark_gray>-</dark_gray> <gray>Changer de force la couleur</gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>/de teamadmin addpoints <équipe> <event> <montant></yellow> <dark_gray>-</dark_gray> <gray>Ajouter des points de tournoi</gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</dark_gray>"));
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subs = List.of("disband", "setcolor", "addpoints");
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return subs.stream().filter(s -> s.startsWith(prefix)).toList();
        }

        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return teamManager.getTeams().stream()
                .map(DanaTeam::getId)
                .filter(id -> id.startsWith(prefix))
                .toList();
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[2].toLowerCase(Locale.ROOT);
            if (sub.equals("setcolor")) {
                return Arrays.stream(TeamColor.values())
                    .map(TeamColor::getId)
                    .filter(c -> c.startsWith(prefix))
                    .toList();
            } else if (sub.equals("addpoints")) {
                return List.of("boatrace");
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("addpoints")) {
            return List.of("10", "25", "50", "100");
        }

        return Collections.emptyList();
    }
}
