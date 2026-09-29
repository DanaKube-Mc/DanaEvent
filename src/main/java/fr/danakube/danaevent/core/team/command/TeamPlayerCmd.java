package fr.danakube.danaevent.core.team.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.core.team.gui.TeamColorPickerGui;
import fr.danakube.danaevent.core.team.manager.TeamManager;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import fr.danakube.danaevent.core.team.model.TeamMember;
import fr.danakube.danaevent.core.team.model.TeamRole;
import fr.danakube.danaevent.core.team.model.TeamScoreEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles /de team subcommands for players: creating teams, invites, color selection,
 * kicking, disbanding, leaving, viewing team info, and leaderboards.
 */
public class TeamPlayerCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final TeamManager teamManager;

    public TeamPlayerCmd(@NotNull DanaEventPlugin plugin, @NotNull TeamManager teamManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.teamManager = Objects.requireNonNull(teamManager, "teamManager cannot be null");
    }

    @Override
    public @NotNull String getName() {
        return "team";
    }

    @Override
    public @NotNull List<String> getAliases() {
        return List.of("t");
    }

    @Override
    public @NotNull String getDescription() {
        return "Gestion des équipes d'événements";
    }

    @Override
    public @NotNull String getSyntax() {
        return "/de team <create|color|invite|accept|decline|kick|leave|disband|info|top>";
    }

    @Override
    public @NotNull String getPermission() {
        return "";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "color" -> handleColor(sender);
            case "invite" -> handleInvite(sender, args);
            case "accept" -> handleAccept(sender, args);
            case "decline" -> handleDecline(sender, args);
            case "kick" -> handleKick(sender, args);
            case "leave" -> handleLeave(sender);
            case "disband" -> handleDisband(sender);
            case "info" -> handleInfo(sender, args);
            case "top" -> handleTop(sender, args);
            default -> sendHelp(sender);
        }
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendMessage(sender, "player-only");
            return;
        }

        if (!player.hasPermission("danaevent.team.create")) {
            plugin.getMessageManager().sendMessage(player, "no-permission");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<prefix> <red>Utilisation : <yellow>/de team create <nom></yellow></red>"
            ));
            return;
        }

        String name = args[1].trim();
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (!TeamManager.TEAM_ID_PATTERN.matcher(id).matches()) {
            plugin.getMessageManager().sendMessage(player, "team-id-invalid");
            return;
        }

        if (teamManager.hasTeam(player.getUniqueId())) {
            DanaTeam current = teamManager.getPlayerTeam(player.getUniqueId()).orElse(null);
            plugin.getMessageManager().sendMessage(
                player,
                "team-already-in-team",
                Placeholder.parsed("team", current != null ? current.getFormattedName() : "")
            );
            return;
        }

        if (teamManager.getTeam(id).isPresent()) {
            plugin.getMessageManager().sendMessage(player, "team-id-taken");
            return;
        }

        Optional<TeamColor> availableColor = teamManager.getFirstAvailableColor();
        if (availableColor.isEmpty()) {
            plugin.getMessageManager().sendMessage(player, "team-no-colors-available");
            return;
        }

        teamManager.createTeam(id, name, availableColor.get(), player);
    }

    private void handleColor(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendMessage(sender, "player-only");
            return;
        }

        Optional<DanaTeam> teamOpt = teamManager.getPlayerTeam(player.getUniqueId());
        if (teamOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(player, "team-not-in-team");
            return;
        }

        DanaTeam team = teamOpt.get();
        if (!team.isLeader(player.getUniqueId())) {
            plugin.getMessageManager().sendMessage(player, "team-not-leader");
            return;
        }

        new TeamColorPickerGui(plugin, teamManager).open(player, team);
    }

    private void handleInvite(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendMessage(sender, "player-only");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<prefix> <red>Utilisation : <yellow>/de team invite <joueur></yellow></red>"
            ));
            return;
        }

        Optional<DanaTeam> teamOpt = teamManager.getPlayerTeam(player.getUniqueId());
        if (teamOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(player, "team-not-in-team");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            plugin.getMessageManager().sendMessage(
                player,
                "team-player-not-found",
                Placeholder.parsed("player", args[1])
            );
            return;
        }

        if (teamManager.hasTeam(target.getUniqueId())) {
            plugin.getMessageManager().sendMessage(
                player,
                "team-player-already-in-team",
                Placeholder.parsed("player", target.getName())
            );
            return;
        }

        teamManager.invitePlayer(teamOpt.get().getId(), player.getUniqueId(), target);
    }

    private void handleAccept(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendMessage(sender, "player-only");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<prefix> <red>Utilisation : <yellow>/de team accept <nom_equipe></yellow></red>"
            ));
            return;
        }

        teamManager.acceptInvite(player, args[1]);
    }

    private void handleDecline(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendMessage(sender, "player-only");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<prefix> <red>Utilisation : <yellow>/de team decline <nom_equipe></yellow></red>"
            ));
            return;
        }

        teamManager.declineInvite(player.getUniqueId(), args[1]);
    }

    private void handleKick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendMessage(sender, "player-only");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<prefix> <red>Utilisation : <yellow>/de team kick <joueur></yellow></red>"
            ));
            return;
        }

        Optional<DanaTeam> teamOpt = teamManager.getPlayerTeam(player.getUniqueId());
        if (teamOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(player, "team-not-in-team");
            return;
        }

        DanaTeam team = teamOpt.get();
        if (!team.isLeader(player.getUniqueId())) {
            plugin.getMessageManager().sendMessage(player, "team-not-leader");
            return;
        }

        String targetName = args[1];
        UUID targetUuid = null;
        for (TeamMember m : team.getMembers().values()) {
            if (m.lastKnownName() != null && m.lastKnownName().equalsIgnoreCase(targetName)) {
                targetUuid = m.playerUuid();
                break;
            }
        }

        if (targetUuid == null) {
            Player onlineTarget = Bukkit.getPlayer(targetName);
            if (onlineTarget != null && team.hasMember(onlineTarget.getUniqueId())) {
                targetUuid = onlineTarget.getUniqueId();
            }
        }

        if (targetUuid == null) {
            plugin.getMessageManager().sendMessage(
                player,
                "team-player-not-found",
                Placeholder.parsed("player", targetName)
            );
            return;
        }

        if (targetUuid.equals(player.getUniqueId())) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<prefix> <red>Vous ne pouvez pas vous expulser vous-même. Utilisez /de team leave.</red>"
            ));
            return;
        }

        teamManager.kickMember(team.getId(), player.getUniqueId(), targetUuid);
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendMessage(sender, "player-only");
            return;
        }
        teamManager.leaveTeam(player);
    }

    private void handleDisband(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendMessage(sender, "player-only");
            return;
        }

        Optional<DanaTeam> teamOpt = teamManager.getPlayerTeam(player.getUniqueId());
        if (teamOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(player, "team-not-in-team");
            return;
        }

        DanaTeam team = teamOpt.get();
        if (!team.isLeader(player.getUniqueId())) {
            plugin.getMessageManager().sendMessage(player, "team-not-leader");
            return;
        }

        teamManager.disbandTeam(team.getId());
    }

    private void handleInfo(CommandSender sender, String[] args) {
        DanaTeam team = null;
        if (args.length >= 2) {
            team = teamManager.getTeam(args[1]).orElse(null);
        } else if (sender instanceof Player player) {
            team = teamManager.getPlayerTeam(player.getUniqueId()).orElse(null);
        }

        if (team == null) {
            if (args.length >= 2) {
                plugin.getMessageManager().sendMessage(sender, "team-not-found");
            } else {
                plugin.getMessageManager().sendMessage(sender, "team-not-in-team");
            }
            return;
        }

        Player leaderPlayer = Bukkit.getPlayer(team.getLeaderUuid());
        String leaderName = leaderPlayer != null ? leaderPlayer.getName() : "Inconnu";
        for (TeamMember m : team.getMembers().values()) {
            if (m.role() == TeamRole.LEADER && m.lastKnownName() != null) {
                leaderName = m.lastKnownName();
                break;
            }
        }

        sender.sendMessage(MiniMessage.miniMessage().deserialize(
            "<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬</dark_gray> " + team.getFormattedName() + " <dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬</dark_gray>"
        ));
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
            "<gray>• Identifiant : <white>" + team.getId() + "</white></gray>"
        ));
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
            "<gray>• Chef : <yellow>" + leaderName + "</yellow></gray>"
        ));
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
            "<gray>• Couleur : " + team.getColor().getDisplayName() + "</gray>"
        ));
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
            "<gray>• Membres (<yellow>" + team.getMemberCount() + "</yellow>) :</gray>"
        ));

        for (TeamMember member : team.getMembers().values()) {
            Player p = Bukkit.getPlayer(member.playerUuid());
            boolean online = p != null && p.isOnline();
            String status = online ? "<green>●</green>" : "<dark_gray>○</dark_gray>";
            String roleTag = member.role() == TeamRole.LEADER ? " <gold>[Chef]</gold>" : "";
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "  " + status + " <white>" + member.lastKnownName() + "</white>" + roleTag
            ));
        }
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</dark_gray>"));
    }

    private void handleTop(CommandSender sender, String[] args) {
        String eventType = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "boatrace";
        String periodMonth = plugin.getTeamScoreManager() != null
            ? plugin.getTeamScoreManager().getCurrentPeriodMonth()
            : "2026-09";

        if (plugin.getTeamScoreManager() == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<prefix> <red>Le service de scoring n'est pas initialisé.</red>"
            ));
            return;
        }

        plugin.getTeamScoreManager().getLeaderboard(eventType, periodMonth, 10).thenAccept(entries -> {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<dark_gray>▬▬▬▬▬▬▬▬</dark_gray> <gradient:#00c6ff:#0072ff><b>Classement Équipes : "
                + eventType.toUpperCase(Locale.ROOT) + " (" + periodMonth + ")</b></gradient> <dark_gray>▬▬▬▬▬▬▬▬</dark_gray>"
            ));

            if (entries.isEmpty()) {
                sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gray><i>Aucun score enregistré pour cette période.</i></gray>"
                ));
            } else {
                for (int i = 0; i < entries.size(); i++) {
                    TeamScoreEntry entry = entries.get(i);
                    DanaTeam team = teamManager.getTeam(entry.teamId()).orElse(null);
                    String teamDisplay = team != null ? team.getFormattedName() : "<yellow>" + entry.teamId() + "</yellow>";
                    sender.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<gold>#" + (i + 1) + "</gold> " + teamDisplay + " <dark_gray>-</dark_gray> <aqua>"
                        + String.format(Locale.ROOT, "%.1f", entry.scoreValue()) + " pts</aqua>"
                    ));
                }
            }
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</dark_gray>"));
        });
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
            "<dark_gray>▬▬▬▬▬▬▬▬</dark_gray> <gradient:#00c6ff:#0072ff><b>Commandes Équipe</b></gradient> <dark_gray>▬▬▬▬▬▬▬▬</dark_gray>"
        ));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>/de team create <nom></yellow> <dark_gray>-</dark_gray> <gray>Créer une équipe</gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>/de team color</yellow> <dark_gray>-</dark_gray> <gray>Choisir la couleur (Chef)</gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>/de team invite <joueur></yellow> <dark_gray>-</dark_gray> <gray>Inviter un joueur</gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>/de team accept <équipe></yellow> <dark_gray>-</dark_gray> <gray>Accepter une invitation</gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>/de team decline <équipe></yellow> <dark_gray>-</dark_gray> <gray>Refuser une invitation</gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>/de team kick <joueur></yellow> <dark_gray>-</dark_gray> <gray>Expulser un membre (Chef)</gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>/de team leave</yellow> <dark_gray>-</dark_gray> <gray>Quitter votre équipe</gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>/de team disband</yellow> <dark_gray>-</dark_gray> <gray>Dissoudre votre équipe (Chef)</gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>/de team info [nom]</yellow> <dark_gray>-</dark_gray> <gray>Voir les informations d'une équipe</gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>/de team top [event]</yellow> <dark_gray>-</dark_gray> <gray>Voir le classement des équipes</gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</dark_gray>"));
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = List.of("create", "color", "invite", "accept", "decline", "kick", "leave", "disband", "info", "top");
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return subs.stream().filter(s -> s.startsWith(prefix)).toList();
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);

            switch (sub) {
                case "invite" -> {
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
                }
                case "accept", "decline" -> {
                    if (sender instanceof Player p) {
                        return teamManager.getPendingInvites(p.getUniqueId()).stream()
                            .map(invite -> invite.teamId())
                            .filter(t -> t.toLowerCase(Locale.ROOT).startsWith(prefix))
                            .toList();
                    }
                }
                case "kick" -> {
                    if (sender instanceof Player p) {
                        return teamManager.getPlayerTeam(p.getUniqueId())
                            .map(t -> t.getMembers().values().stream()
                                .map(TeamMember::lastKnownName)
                                .filter(Objects::nonNull)
                                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                                .toList())
                            .orElse(List.of());
                    }
                }
                case "info" -> {
                    return teamManager.getTeams().stream()
                        .map(DanaTeam::getId)
                        .filter(t -> t.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
                }
                case "top" -> {
                    return List.of("boatrace");
                }
            }
        }

        return Collections.emptyList();
    }
}
