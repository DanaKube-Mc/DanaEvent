package fr.danakube.danaevent.core.module;

import fr.danakube.danaevent.core.command.SubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Base abstract implementation of {@link DanaModule} providing default lifecycle and command management.
 */
public abstract class AbstractDanaModule implements DanaModule {

    private final String id;
    private final String name;
    private final String version;
    private final List<SubCommand> subCommands = new ArrayList<>();
    private boolean enabled;

    protected AbstractDanaModule(String id, String name, String version) {
        this.id = Objects.requireNonNull(id, "Module id cannot be null").toLowerCase();
        this.name = Objects.requireNonNull(name, "Module name cannot be null");
        this.version = Objects.requireNonNull(version, "Module version cannot be null");
        this.enabled = false;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    protected void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void onEnable() {
        this.enabled = true;
    }

    @Override
    public void onDisable() {
        this.enabled = false;
    }

    @Override
    public void onReload() {
        onDisable();
        onEnable();
    }

    @Override
    public List<SubCommand> getSubCommands() {
        return Collections.unmodifiableList(subCommands);
    }

    /**
     * Registers a subcommand for this module.
     *
     * @param subCommand the subcommand to add
     */
    protected void registerSubCommand(SubCommand subCommand) {
        if (subCommand != null) {
            subCommands.add(subCommand);
        }
    }
}
