package fr.danakube.danaevent.core.module;

import fr.danakube.danaevent.core.command.SubCommand;

import java.util.List;

/**
 * Interface representing a pluggable DanaEvent module.
 */
public interface DanaModule {

    /**
     * @return unique identifier of the module (lowercase)
     */
    String getId();

    /**
     * @return display name of the module
     */
    String getName();

    /**
     * @return version string of the module
     */
    String getVersion();

    /**
     * Lifecycle callback when the module is enabled.
     */
    void onEnable();

    /**
     * Lifecycle callback when the module is disabled.
     */
    void onDisable();

    /**
     * Lifecycle callback when the module is reloaded.
     */
    void onReload();

    /**
     * @return list of subcommands provided by this module
     */
    List<SubCommand> getSubCommands();

    /**
     * @return true if the module is currently active and running
     */
    boolean isEnabled();
}
