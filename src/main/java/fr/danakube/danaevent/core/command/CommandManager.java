package fr.danakube.danaevent.core.command;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.module.DanaModule;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Root command dispatcher for /danaevent and /de.
 * Routes subcommands to either Core SubCommands or registered DanaModule SubCommands.
 */
public class CommandManager implements CommandExecutor, TabCompleter {

    private final DanaEventPlugin plugin;
    private final Map<String, SubCommand> coreCommands = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, SubCommand> commandAliases = new ConcurrentHashMap<>();

    public CommandManager(DanaEventPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        registerCoreCommand(new HelpCommand(plugin));
        registerCoreCommand(new ReloadCommand(plugin));
        registerCoreCommand(new ModulesCommand(plugin));
        registerCoreCommand(new WandCommand(plugin));
    }

    /**
     * Registers a Core subcommand.
     *
     * @param subCommand the subcommand to register
     */
    public void registerCoreCommand(SubCommand subCommand) {
        Objects.requireNonNull(subCommand, "subCommand cannot be null");
        String name = Objects.requireNonNull(subCommand.getName(), "subCommand name cannot be null").toLowerCase();
        coreCommands.put(name, subCommand);

        if (subCommand.getAliases() != null) {
            for (String alias : subCommand.getAliases()) {
                commandAliases.put(alias.toLowerCase(), subCommand);
            }
        }
    }

    /**
     * Unregisters a Core subcommand.
     *
     * @param name name or alias of the subcommand to unregister
     */
    public void unregisterCoreCommand(String name) {
        if (name == null) {
            return;
        }
        SubCommand removed = coreCommands.remove(name.toLowerCase());
        if (removed != null && removed.getAliases() != null) {
            for (String alias : removed.getAliases()) {
                commandAliases.remove(alias.toLowerCase());
            }
        }
        commandAliases.remove(name.toLowerCase());
    }

    /**
     * @return unmodifiable map of registered core subcommands
     */
    public Map<String, SubCommand> getCoreCommands() {
        synchronized (coreCommands) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(coreCommands));
        }
    }

    /**
     * Cleans up all registered commands and aliases.
     */
    public void cleanUp() {
        coreCommands.clear();
        commandAliases.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            SubCommand helpCmd = coreCommands.get("help");
            if (helpCmd != null) {
                if (helpCmd.getPermission() != null && !helpCmd.getPermission().isEmpty() && !sender.hasPermission(helpCmd.getPermission())) {
                    plugin.getMessageManager().sendMessage(sender, "no-permission");
                    return true;
                }
                helpCmd.execute(sender, new String[0]);
                return true;
            }
            plugin.getMessageManager().sendMessage(sender, "error-generic");
            return true;
        }

        String firstArg = args[0].toLowerCase();

        // 1. Dispatch to Core subcommand
        SubCommand coreCmd = findCoreCommand(firstArg);
        if (coreCmd != null) {
            return executeSubCommand(sender, coreCmd, Arrays.copyOfRange(args, 1, args.length));
        }

        // 2. Dispatch to Module subcommand
        Optional<DanaModule> moduleOpt = plugin.getModuleManager().getModule(firstArg);
        if (moduleOpt.isPresent()) {
            DanaModule module = moduleOpt.get();
            if (!module.isEnabled()) {
                plugin.getMessageManager().sendMessage(sender, "module-disabled", Placeholder.parsed("module", module.getName()));
                return true;
            }

            if (args.length < 2) {
                displayModuleHelp(sender, module);
                return true;
            }

            String moduleSubName = args[1].toLowerCase();
            SubCommand moduleSub = findModuleSubCommand(module, moduleSubName);
            if (moduleSub != null) {
                return executeSubCommand(sender, moduleSub, Arrays.copyOfRange(args, 2, args.length));
            }

            plugin.getMessageManager().sendMessage(sender, "command-unknown");
            return true;
        }

        // 3. Command or module not found
        plugin.getMessageManager().sendMessage(sender, "module-not-found", Placeholder.parsed("module", args[0]));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();

            // 1. Suggest allowed Core subcommands
            for (SubCommand cmd : coreCommands.values()) {
                if (cmd.getPermission() == null || cmd.getPermission().isEmpty() || sender.hasPermission(cmd.getPermission())) {
                    completions.add(cmd.getName());
                }
            }

            // 2. Suggest active modules
            for (DanaModule mod : plugin.getModuleManager().getModules()) {
                if (mod.isEnabled()) {
                    completions.add(mod.getId());
                }
            }

            List<String> matched = StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
            Collections.sort(matched);
            return matched;
        }

        if (args.length >= 2) {
            String first = args[0].toLowerCase();

            // First arg is Core command
            SubCommand coreCmd = findCoreCommand(first);
            if (coreCmd != null) {
                if (coreCmd.getPermission() == null || coreCmd.getPermission().isEmpty() || sender.hasPermission(coreCmd.getPermission())) {
                    return coreCmd.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
                }
                return Collections.emptyList();
            }

            // First arg is Module ID
            Optional<DanaModule> modOpt = plugin.getModuleManager().getModule(first);
            if (modOpt.isPresent() && modOpt.get().isEnabled()) {
                DanaModule module = modOpt.get();

                if (args.length == 2) {
                    List<String> subNames = new ArrayList<>();
                    for (SubCommand cmd : module.getSubCommands()) {
                        if (cmd.getPermission() == null || cmd.getPermission().isEmpty() || sender.hasPermission(cmd.getPermission())) {
                            subNames.add(cmd.getName());
                        }
                    }
                    List<String> matched = StringUtil.copyPartialMatches(args[1], subNames, new ArrayList<>());
                    Collections.sort(matched);
                    return matched;
                } else {
                    SubCommand moduleSub = findModuleSubCommand(module, args[1].toLowerCase());
                    if (moduleSub != null) {
                        if (moduleSub.getPermission() == null || moduleSub.getPermission().isEmpty() || sender.hasPermission(moduleSub.getPermission())) {
                            return moduleSub.tabComplete(sender, Arrays.copyOfRange(args, 2, args.length));
                        }
                    }
                    return Collections.emptyList();
                }
            }
        }

        return Collections.emptyList();
    }

    private SubCommand findCoreCommand(String name) {
        SubCommand direct = coreCommands.get(name.toLowerCase());
        if (direct != null) {
            return direct;
        }
        return commandAliases.get(name.toLowerCase());
    }

    private SubCommand findModuleSubCommand(DanaModule module, String name) {
        for (SubCommand cmd : module.getSubCommands()) {
            if (cmd.getName().equalsIgnoreCase(name)) {
                return cmd;
            }
            if (cmd.getAliases() != null) {
                for (String alias : cmd.getAliases()) {
                    if (alias.equalsIgnoreCase(name)) {
                        return cmd;
                    }
                }
            }
        }
        return null;
    }

    private boolean executeSubCommand(CommandSender sender, SubCommand subCmd, String[] args) {
        if (subCmd.getPermission() != null && !subCmd.getPermission().isEmpty() && !sender.hasPermission(subCmd.getPermission())) {
            plugin.getMessageManager().sendMessage(sender, "no-permission");
            return true;
        }

        if (subCmd.isPlayerOnly() && !(sender instanceof Player)) {
            plugin.getMessageManager().sendMessage(sender, "player-only");
            return true;
        }

        try {
            subCmd.execute(sender, args);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error executing subcommand '" + subCmd.getName() + "'", e);
            plugin.getMessageManager().sendMessage(sender, "error-generic");
        }
        return true;
    }

    private void displayModuleHelp(CommandSender sender, DanaModule module) {
        List<SubCommand> commands = module.getSubCommands().stream()
            .filter(c -> c.getPermission() == null || c.getPermission().isEmpty() || sender.hasPermission(c.getPermission()))
            .toList();

        plugin.getMessageManager().sendRawMessage(sender, "<dark_gray>----------------------------------------</dark_gray>");
        plugin.getMessageManager().sendRawMessage(sender, "<prefix> <gray>Commandes du module <aqua>" + module.getName() + "</aqua> :</gray>");
        if (commands.isEmpty()) {
            plugin.getMessageManager().sendRawMessage(sender, "<gray>Aucune commande disponible.</gray>");
        } else {
            for (SubCommand cmd : commands) {
                plugin.getMessageManager().sendRawMessage(
                    sender,
                    "<yellow>" + cmd.getSyntax() + "</yellow> <dark_gray>-</dark_gray> <gray>" + cmd.getDescription() + "</gray>"
                );
            }
        }
        plugin.getMessageManager().sendRawMessage(sender, "<dark_gray>----------------------------------------</dark_gray>");
    }
}
