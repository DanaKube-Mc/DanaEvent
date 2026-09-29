package fr.danakube.danaevent.core.module;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages module registration, lifecycle, status tracking and fault isolation.
 */
public class ModuleManager {

    private final JavaPlugin plugin;
    private final Map<String, DanaModule> modules = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, ModuleStatus> statuses = new ConcurrentHashMap<>();

    public ModuleManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    /**
     * Registers a module in the registry.
     *
     * @param module the module to register
     */
    public void registerModule(DanaModule module) {
        Objects.requireNonNull(module, "module cannot be null");
        String id = Objects.requireNonNull(module.getId(), "module id cannot be null").toLowerCase();

        if (modules.containsKey(id)) {
            throw new IllegalArgumentException("Module with id '" + id + "' is already registered.");
        }

        modules.put(id, module);
        statuses.put(id, module.isEnabled() ? ModuleStatus.ENABLED : ModuleStatus.DISABLED);
        plugin.getLogger().info("Module '" + module.getName() + "' (id: " + id + ", v" + module.getVersion() + ") registered.");
    }

    /**
     * Enables a module by id with fault isolation.
     *
     * @param id module identifier
     * @return true if enabled successfully, false otherwise
     */
    public boolean enableModule(String id) {
        if (id == null) {
            return false;
        }
        DanaModule module = modules.get(id.toLowerCase());
        if (module == null) {
            return false;
        }

        if (module.isEnabled() && statuses.get(id.toLowerCase()) == ModuleStatus.ENABLED) {
            return true;
        }

        try {
            module.onEnable();
            statuses.put(module.getId().toLowerCase(), ModuleStatus.ENABLED);
            plugin.getLogger().info("Module '" + module.getName() + "' enabled successfully.");
            return true;
        } catch (Throwable t) {
            statuses.put(module.getId().toLowerCase(), ModuleStatus.ERROR);
            plugin.getLogger().log(Level.SEVERE, "Failed to enable module '" + module.getId() + "': " + t.getMessage(), t);
            try {
                module.onDisable();
            } catch (Throwable disableEx) {
                plugin.getLogger().log(Level.WARNING, "Error while disabling failed module '" + module.getId() + "'", disableEx);
            }
            return false;
        }
    }

    /**
     * Disables a module by id.
     *
     * @param id module identifier
     * @return true if disabled or already disabled, false if not found
     */
    public boolean disableModule(String id) {
        if (id == null) {
            return false;
        }
        DanaModule module = modules.get(id.toLowerCase());
        if (module == null) {
            return false;
        }

        try {
            module.onDisable();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Error while disabling module '" + module.getId() + "': " + t.getMessage(), t);
        } finally {
            statuses.put(module.getId().toLowerCase(), ModuleStatus.DISABLED);
            plugin.getLogger().info("Module '" + module.getName() + "' disabled.");
        }

        return true;
    }

    /**
     * Reloads a module by id with fault isolation.
     *
     * @param id module identifier
     * @return true if reloaded successfully, false otherwise
     */
    public boolean reloadModule(String id) {
        if (id == null) {
            return false;
        }
        DanaModule module = modules.get(id.toLowerCase());
        if (module == null) {
            return false;
        }

        try {
            module.onReload();
            ModuleStatus newStatus = module.isEnabled() ? ModuleStatus.ENABLED : ModuleStatus.DISABLED;
            statuses.put(module.getId().toLowerCase(), newStatus);
            plugin.getLogger().info("Module '" + module.getName() + "' reloaded successfully.");
            return true;
        } catch (Throwable t) {
            statuses.put(module.getId().toLowerCase(), ModuleStatus.ERROR);
            plugin.getLogger().log(Level.SEVERE, "Failed to reload module '" + module.getId() + "': " + t.getMessage(), t);
            try {
                module.onDisable();
            } catch (Throwable disableEx) {
                plugin.getLogger().log(Level.WARNING, "Error while disabling failed module '" + module.getId() + "'", disableEx);
            }
            return false;
        }
    }

    /**
     * Enables all registered modules sequentially, isolating faults so that one module failure does not stop others.
     */
    public void enableAll() {
        synchronized (modules) {
            for (DanaModule module : modules.values()) {
                enableModule(module.getId());
            }
        }
    }

    /**
     * Disables all registered modules.
     */
    public void disableAll() {
        synchronized (modules) {
            for (DanaModule module : modules.values()) {
                disableModule(module.getId());
            }
        }
    }

    /**
     * Reloads all registered modules.
     */
    public void reloadAll() {
        synchronized (modules) {
            for (DanaModule module : modules.values()) {
                reloadModule(module.getId());
            }
        }
    }

    /**
     * Retrieves a module by identifier.
     *
     * @param id module identifier (case-insensitive)
     * @return Optional containing the module if registered
     */
    public Optional<DanaModule> getModule(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(modules.get(id.toLowerCase()));
    }

    /**
     * @return unmodifiable view of all registered modules
     */
    public Collection<DanaModule> getModules() {
        synchronized (modules) {
            return Collections.unmodifiableCollection(new java.util.ArrayList<>(modules.values()));
        }
    }

    /**
     * @param id module identifier (case-insensitive)
     * @return current lifecycle status of the module
     */
    public ModuleStatus getModuleStatus(String id) {
        if (id == null) {
            return ModuleStatus.DISABLED;
        }
        return statuses.getOrDefault(id.toLowerCase(), ModuleStatus.DISABLED);
    }
}
