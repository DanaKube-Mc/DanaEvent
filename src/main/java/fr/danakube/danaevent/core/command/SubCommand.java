package fr.danakube.danaevent.core.command;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Interface representing a subcommand in DanaEvent.
 */
public interface SubCommand {

    /**
     * @return name of the subcommand
     */
    String getName();

    /**
     * @return list of aliases for this subcommand
     */
    List<String> getAliases();

    /**
     * @return brief description of what this subcommand does
     */
    String getDescription();

    /**
     * @return syntax / usage string (e.g. "/danaevent help")
     */
    String getSyntax();

    /**
     * @return required permission node, or null/empty if none
     */
    String getPermission();

    /**
     * @return true if this command can only be executed by a Player
     */
    boolean isPlayerOnly();

    /**
     * Executes the subcommand logic.
     *
     * @param sender command sender (player or console)
     * @param args   arguments passed to this subcommand (excluding root and sub command labels)
     */
    void execute(CommandSender sender, String[] args);

    /**
     * Provides tab-completion suggestions for this subcommand.
     *
     * @param sender command sender
     * @param args   arguments passed to this subcommand
     * @return list of tab completions
     */
    List<String> tabComplete(CommandSender sender, String[] args);
}
