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
    private final Map<String, String> moduleAliases = new ConcurrentHashMap<>();
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

        if (module.getAliases() != null) {
            for (String alias : module.getAliases()) {
                if (alias != null && !alias.isBlank()) {
                    moduleAliases.put(alias.toLowerCase(), id);
                }
            }
        }

        plugin.getLogger().info("Module '" + module.getName() + "' (id: " + id + ", v" + module.getVersion() + ") registered.");
    }

    /**
     * Resolves a module identifier from either its id or registered alias.
     *
     * @param idOrAlias module id or alias
     * @return resolved primary module id, or null if unresolved
     */
    public String resolveModuleId(String idOrAlias) {
        if (idOrAlias == null) {
            return null;
        }
        String lower = idOrAlias.toLowerCase();
        if (modules.containsKey(lower)) {
            return lower;
        }
        return moduleAliases.get(lower);
    }

    /**
     * Enables a module by id or alias with fault isolation.
     *
     * @param id module identifier or alias
     * @return true if enabled successfully, false otherwise
     */
    public boolean enableModule(String id) {
        String resolvedId = resolveModuleId(id);
        if (resolvedId == null) {
            return false;
        }
        DanaModule module = modules.get(resolvedId);
        if (module == null) {
            return false;
        }

        if (module.isEnabled() && statuses.get(resolvedId) == ModuleStatus.ENABLED) {
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
     * Disables a module by id or alias.
     *
     * @param id module identifier or alias
     * @return true if disabled or already disabled, false if not found
     */
    public boolean disableModule(String id) {
        String resolvedId = resolveModuleId(id);
        if (resolvedId == null) {
            return false;
        }
        DanaModule module = modules.get(resolvedId);
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
     * Reloads a module by id or alias with fault isolation.
     *
     * @param id module identifier or alias
     * @return true if reloaded successfully, false otherwise
     */
    public boolean reloadModule(String id) {
        String resolvedId = resolveModuleId(id);
        if (resolvedId == null) {
            return false;
        }
        DanaModule module = modules.get(resolvedId);
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
     * Retrieves a module by identifier or alias.
     *
     * @param id module identifier or alias (case-insensitive)
     * @return Optional containing the module if registered
     */
    public Optional<DanaModule> getModule(String id) {
        String resolvedId = resolveModuleId(id);
        if (resolvedId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(modules.get(resolvedId));
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
     * @return collection of currently enabled modules
     */
    public Collection<DanaModule> getEnabledModules() {
        synchronized (modules) {
            return modules.values().stream()
                .filter(m -> statuses.get(m.getId().toLowerCase()) == ModuleStatus.ENABLED)
                .toList();
        }
    }

    /**
     * @param id module identifier or alias (case-insensitive)
     * @return current lifecycle status of the module
     */
    public ModuleStatus getModuleStatus(String id) {
        String resolvedId = resolveModuleId(id);
        if (resolvedId == null) {
            return ModuleStatus.DISABLED;
        }
        return statuses.getOrDefault(resolvedId, ModuleStatus.DISABLED);
    }
}
