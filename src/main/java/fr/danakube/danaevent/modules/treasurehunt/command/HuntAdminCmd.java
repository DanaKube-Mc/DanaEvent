package fr.danakube.danaevent.modules.treasurehunt.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.command.SubCommand;
import fr.danakube.danaevent.core.selection.CuboidRegion;
import fr.danakube.danaevent.modules.treasurehunt.TreasureHuntModule;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntMode;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntPathType;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntStep;
import fr.danakube.danaevent.modules.treasurehunt.model.StepTriggerType;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Admin subcommand dispatcher: /de hunt admin ...
 * Allows in-game creation, step setup, rewards and lifecycle control of treasure hunts.
 */
public class HuntAdminCmd implements SubCommand {

    private final DanaEventPlugin plugin;
    private final TreasureHuntModule module;

    public HuntAdminCmd(DanaEventPlugin plugin, TreasureHuntModule module) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.module = Objects.requireNonNull(module, "module cannot be null");
    }

    @Override
    public String getName() {
        return "admin";
    }

    @Override
    public List<String> getAliases() {
        return List.of("manage");
    }

    @Override
    public String getDescription() {
        return "Commandes d'administration des chasses au trésor";
    }

    @Override
    public String getSyntax() {
        return "/de hunt admin <create|step|setfinalreward|toggle|resetranking> ...";
    }

    @Override
    public String getPermission() {
        return "danaevent.treasurehunt.admin";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sendHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "step" -> handleStep(sender, args);
            case "setfinalreward" -> handleSetFinalReward(sender, args);
            case "toggle" -> handleToggle(sender, args);
            case "resetranking" -> handleResetRanking(sender, args);
            default -> sendHelp(sender);
        }
    }

    private void handleCreate(CommandSender sender, String[] args) {
        // Syntax: /de hunt admin create <id> <SOLO|TEAM> <LINEAR|RANDOM>
        if (args.length < 4) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Syntaxe : /de hunt admin create <id> <SOLO|TEAM> <LINEAR|RANDOM></red>");
            return;
        }

        String huntId = args[1].trim().toLowerCase();
        if (!huntId.matches("^[a-z0-9_-]{3,32}$")) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>L'ID doit être composé de 3 à 32 caractères alphanumériques.</red>");
            return;
        }

        if (module.getHuntConfig().getHunt(huntId).isPresent()) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Une chasse avec cet ID existe déjà.</red>");
            return;
        }

        HuntMode mode = HuntMode.fromString(args[2]);
        HuntPathType pathType = HuntPathType.fromString(args[3]);

        Hunt hunt = module.getHuntConfig().createHunt(huntId, huntId, mode, pathType);
        module.getHuntConfig().saveHunts();

        plugin.getMessageManager().sendMessage(
            sender,
            "hunt-admin-created",
            Placeholder.parsed("hunt", hunt.getId()),
            Placeholder.parsed("mode", mode.name()),
            Placeholder.parsed("type", pathType.name())
        );
    }

    private void handleStep(CommandSender sender, String[] args) {
        // Syntax: /de hunt admin step add click/zone/chat <chasse> ...
        //         /de hunt admin step setclue <chasse> <numéro> <texte...>
        //         /de hunt admin step setreward <chasse> <numéro>
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "add" -> handleStepAdd(sender, args);
            case "setclue" -> handleStepSetClue(sender, args);
            case "setreward" -> handleStepSetReward(sender, args);
            default -> sendHelp(sender);
        }
    }

    private void handleStepAdd(CommandSender sender, String[] args) {
        // /de hunt admin step add click <chasse>
        // /de hunt admin step add zone <chasse>
        // /de hunt admin step add chat <chasse> <mot_de_passe>
        if (args.length < 4) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Syntaxe : /de hunt admin step add <click|zone|chat> <chasse> [réponse]</red>");
            return;
        }

        String typeStr = args[2].toLowerCase();
        String huntId = args[3].trim().toLowerCase();
        Optional<Hunt> huntOpt = module.getHuntConfig().getHunt(huntId);
        if (huntOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(sender, "hunt-not-found", Placeholder.parsed("hunt", huntId));
            return;
        }

        Hunt hunt = huntOpt.get();
        int stepNum = hunt.getStepCount() + 1;

        if (typeStr.equals("click")) {
            if (!(sender instanceof Player player)) {
                plugin.getMessageManager().sendMessage(sender, "player-only");
                return;
            }
            Block targetBlock = player.getTargetBlockExact(5);
            if (targetBlock == null) {
                targetBlock = player.getTargetBlock(null, 5);
            }
            if (targetBlock == null || targetBlock.getType().isAir()) {
                plugin.getMessageManager().sendMessage(player, "hunt-admin-no-block");
                return;
            }

            HuntStep step = new HuntStep(stepNum, StepTriggerType.BLOCK_CLICK);
            step.setTargetLocation(targetBlock.getLocation());
            hunt.addStep(step);
            module.getHuntConfig().saveHunts();

            plugin.getMessageManager().sendMessage(
                sender,
                "hunt-admin-step-added",
                Placeholder.parsed("number", String.valueOf(stepNum)),
                Placeholder.parsed("trigger", "BLOCK_CLICK"),
                Placeholder.parsed("hunt", hunt.getId())
            );
        } else if (typeStr.equals("zone")) {
            if (!(sender instanceof Player player)) {
                plugin.getMessageManager().sendMessage(sender, "player-only");
                return;
            }
            Optional<CuboidRegion> regionOpt = plugin.getSelectionManager().getRegion(player.getUniqueId());
            if (regionOpt.isEmpty()) {
                plugin.getMessageManager().sendMessage(player, "hunt-admin-no-selection");
                return;
            }

            HuntStep step = new HuntStep(stepNum, StepTriggerType.ZONE_ENTER);
            step.setTargetRegion(regionOpt.get());
            hunt.addStep(step);
            module.getHuntConfig().saveHunts();

            plugin.getMessageManager().sendMessage(
                sender,
                "hunt-admin-step-added",
                Placeholder.parsed("number", String.valueOf(stepNum)),
                Placeholder.parsed("trigger", "ZONE_ENTER"),
                Placeholder.parsed("hunt", hunt.getId())
            );
        } else if (typeStr.equals("chat")) {
            if (args.length < 5) {
                plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Syntaxe : /de hunt admin step add chat <chasse> <mot_de_passe></red>");
                return;
            }
            String answer = String.join(" ", Arrays.copyOfRange(args, 4, args.length)).trim();

            HuntStep step = new HuntStep(stepNum, StepTriggerType.CHAT_ANSWER);
            step.setChatAnswer(answer);
            hunt.addStep(step);
            module.getHuntConfig().saveHunts();

            plugin.getMessageManager().sendMessage(
                sender,
                "hunt-admin-step-added",
                Placeholder.parsed("number", String.valueOf(stepNum)),
                Placeholder.parsed("trigger", "CHAT_ANSWER"),
                Placeholder.parsed("hunt", hunt.getId())
            );
        }
    }

    private void handleStepSetClue(CommandSender sender, String[] args) {
        // /de hunt admin step setclue <chasse> <numéro> <texte...>
        if (args.length < 5) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Syntaxe : /de hunt admin step setclue <chasse> <numéro> <texte...></red>");
            return;
        }

        String huntId = args[2].trim().toLowerCase();
        Optional<Hunt> huntOpt = module.getHuntConfig().getHunt(huntId);
        if (huntOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(sender, "hunt-not-found", Placeholder.parsed("hunt", huntId));
            return;
        }

        Hunt hunt = huntOpt.get();
        int stepNum;
        try {
            stepNum = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Numéro d'étape invalide.</red>");
            return;
        }

        Optional<HuntStep> stepOpt = hunt.getStep(stepNum);
        if (stepOpt.isEmpty()) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Étape introuvable pour cette chasse.</red>");
            return;
        }

        String clue = String.join(" ", Arrays.copyOfRange(args, 4, args.length)).trim();
        stepOpt.get().setClue(clue);
        module.getHuntConfig().saveHunts();

        plugin.getMessageManager().sendMessage(
            sender,
            "hunt-admin-clue-set",
            Placeholder.parsed("number", String.valueOf(stepNum)),
            Placeholder.parsed("hunt", hunt.getId())
        );
    }

    private void handleStepSetReward(CommandSender sender, String[] args) {
        // /de hunt admin step setreward <chasse> <numéro>
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendMessage(sender, "player-only");
            return;
        }

        if (args.length < 4) {
            plugin.getMessageManager().sendRawMessage(player, "<prefix> <red>Syntaxe : /de hunt admin step setreward <chasse> <numéro></red>");
            return;
        }

        String huntId = args[2].trim().toLowerCase();
        Optional<Hunt> huntOpt = module.getHuntConfig().getHunt(huntId);
        if (huntOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(player, "hunt-not-found", Placeholder.parsed("hunt", huntId));
            return;
        }

        Hunt hunt = huntOpt.get();
        int stepNum;
        try {
            stepNum = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendRawMessage(player, "<prefix> <red>Numéro d'étape invalide.</red>");
            return;
        }

        Optional<HuntStep> stepOpt = hunt.getStep(stepNum);
        if (stepOpt.isEmpty()) {
            plugin.getMessageManager().sendRawMessage(player, "<prefix> <red>Étape introuvable.</red>");
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        stepOpt.get().setRewardItem(held != null && !held.getType().isAir() ? held.clone() : null);
        module.getHuntConfig().saveHunts();

        plugin.getMessageManager().sendMessage(
            player,
            "hunt-admin-reward-set",
            Placeholder.parsed("number", String.valueOf(stepNum))
        );
    }

    private void handleSetFinalReward(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendMessage(sender, "player-only");
            return;
        }

        if (args.length < 2) {
            plugin.getMessageManager().sendRawMessage(player, "<prefix> <red>Syntaxe : /de hunt admin setfinalreward <chasse></red>");
            return;
        }

        String huntId = args[1].trim().toLowerCase();
        Optional<Hunt> huntOpt = module.getHuntConfig().getHunt(huntId);
        if (huntOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(player, "hunt-not-found", Placeholder.parsed("hunt", huntId));
            return;
        }

        Hunt hunt = huntOpt.get();
        ItemStack held = player.getInventory().getItemInMainHand();
        hunt.setFinalRewardItem(held != null && !held.getType().isAir() ? held.clone() : null);
        module.getHuntConfig().saveHunts();

        plugin.getMessageManager().sendMessage(
            player,
            "hunt-admin-final-reward-set",
            Placeholder.parsed("hunt", hunt.getId())
        );
    }

    private void handleToggle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Syntaxe : /de hunt admin toggle <chasse></red>");
            return;
        }

        String huntId = args[1].trim().toLowerCase();
        Optional<Hunt> huntOpt = module.getHuntConfig().getHunt(huntId);
        if (huntOpt.isEmpty()) {
            plugin.getMessageManager().sendMessage(sender, "hunt-not-found", Placeholder.parsed("hunt", huntId));
            return;
        }

        Hunt hunt = huntOpt.get();
        hunt.setEnabled(!hunt.isEnabled());
        module.getHuntConfig().saveHunts();

        String statusStr = hunt.isEnabled() ? "<green>activée</green>" : "<red>désactivée</red>";
        plugin.getMessageManager().sendMessage(
            sender,
            "hunt-admin-toggled",
            Placeholder.parsed("hunt", hunt.getId()),
            Placeholder.parsed("status", statusStr)
        );
    }

    private void handleResetRanking(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().sendRawMessage(sender, "<prefix> <red>Syntaxe : /de hunt admin resetranking <chasse> [YYYY-MM]</red>");
            return;
        }

        String huntId = args[1].trim().toLowerCase();
        String periodMonth = args.length >= 3 ? args[2].trim() : null;

        module.getLeaderboardManager().resetRanking(huntId, periodMonth).thenAccept(count -> {
            plugin.getMessageManager().sendMessage(
                sender,
                "hunt-admin-reset",
                Placeholder.parsed("hunt", huntId),
                Placeholder.parsed("count", String.valueOf(count))
            );
        });
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessageManager().sendRawMessage(sender, "<dark_gray>----------------------------------------</dark_gray>");
        plugin.getMessageManager().sendRawMessage(sender, "<prefix> <gold><b>Commandes Admin Chasse au Trésor :</b></gold>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de hunt admin create <id> <SOLO|TEAM> <LINEAR|RANDOM></yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de hunt admin step add <click|zone|chat> <chasse> [réponse]</yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de hunt admin step setclue <chasse> <numéro> <texte...></yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de hunt admin step setreward <chasse> <numéro></yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de hunt admin setfinalreward <chasse></yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de hunt admin toggle <chasse></yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<yellow>/de hunt admin resetranking <chasse> [YYYY-MM]</yellow>");
        plugin.getMessageManager().sendRawMessage(sender, "<dark_gray>----------------------------------------</dark_gray>");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("create", "step", "setfinalreward", "toggle", "resetranking").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .toList();
        }

        if (args[0].equalsIgnoreCase("create")) {
            if (args.length == 3) return List.of("SOLO", "TEAM");
            if (args.length == 4) return List.of("LINEAR", "RANDOM");
        }

        if (args[0].equalsIgnoreCase("step")) {
            if (args.length == 2) {
                return List.of("add", "setclue", "setreward").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
            }
            if (args[1].equalsIgnoreCase("add") && args.length == 3) {
                return List.of("click", "zone", "chat").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .toList();
            }
            if (args.length >= 3) {
                return module.getHuntConfig().getHunts().stream()
                    .map(Hunt::getId)
                    .filter(id -> id.startsWith(args[args.length - 1].toLowerCase()))
                    .toList();
            }
        }

        if (args[0].equalsIgnoreCase("toggle") || args[0].equalsIgnoreCase("resetranking") || args[0].equalsIgnoreCase("setfinalreward")) {
            if (args.length == 2) {
                return module.getHuntConfig().getHunts().stream()
                    .map(Hunt::getId)
                    .filter(id -> id.startsWith(args[1].toLowerCase()))
                    .toList();
            }
        }

        return List.of();
    }
}
