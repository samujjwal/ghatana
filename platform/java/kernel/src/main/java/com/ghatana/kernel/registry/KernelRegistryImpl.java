package com.ghatana.kernel.registry;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.plugin.KernelPlugin;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Default implementation of KernelRegistry.
 *
 * <p>Provides module/plugin discovery, registration, and dependency resolution
 * with topological sorting for lifecycle ordering.</p>
 *
 * @doc.type class
 * @doc.purpose Default implementation of kernel registry with dependency resolution
 * @doc.layer core
 * @doc.pattern Service, Registry
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class KernelRegistryImpl implements KernelRegistry {

    private final Map<String, KernelModule> modules = new ConcurrentHashMap<>();
    private final Map<String, KernelPlugin> plugins = new ConcurrentHashMap<>();
    private final Set<KernelCapability> capabilities = ConcurrentHashMap.newKeySet();
    private final Map<Class<?>, Object> dependencies = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> dependencyByName = new ConcurrentHashMap<>();

    @Override
    public void registerModule(KernelModule module) {
        Objects.requireNonNull(module, "module cannot be null");

        String moduleId = module.getModuleId();
        if (modules.containsKey(moduleId)) {
            throw new IllegalStateException("Module already registered: " + moduleId);
        }

        // Validate dependencies before registration
        List<String> validationErrors = getDependencyValidationErrors(module);
        if (!validationErrors.isEmpty()) {
            throw new IllegalStateException("Dependency validation failed: " + validationErrors);
        }

        modules.put(moduleId, module);

        // Register provided capabilities
        module.getCapabilities().forEach(capabilities::add);
    }

    @Override
    public void registerPlugin(KernelPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");

        String pluginId = plugin.getModuleId();
        if (plugins.containsKey(pluginId)) {
            throw new IllegalStateException("Plugin already registered: " + pluginId);
        }

        plugins.put(pluginId, plugin);

        // Register plugin capabilities
        plugin.getCapabilities().forEach(capabilities::add);
    }

    @Override
    public void registerCapability(KernelCapability capability) {
        Objects.requireNonNull(capability, "capability cannot be null");
        capabilities.add(capability);
    }

    @Override
    public boolean unregisterModule(String moduleId) {
        KernelModule removed = modules.remove(moduleId);
        if (removed != null) {
            // Remove capabilities provided by this module
            removed.getCapabilities().forEach(capabilities::remove);
        }
        return removed != null;
    }

    @Override
    public Optional<KernelModule> getModule(String moduleId) {
        return Optional.ofNullable(modules.get(moduleId));
    }

    @Override
    public Optional<KernelPlugin> getPlugin(String pluginId) {
        return Optional.ofNullable(plugins.get(pluginId));
    }

    @Override
    public List<KernelModule> getAllModules() {
        return new ArrayList<>(modules.values());
    }

    @Override
    public List<KernelPlugin> getAllPlugins() {
        return new ArrayList<>(plugins.values());
    }

    @Override
    public List<KernelCapability> getAllCapabilities() {
        return new ArrayList<>(capabilities);
    }

    @Override
    public List<KernelPlugin> getPluginsByCapability(KernelCapability capability) {
        return plugins.values().stream()
            .filter(p -> p.getCapabilities().contains(capability))
            .collect(Collectors.toList());
    }

    @Override
    public List<KernelModule> getModulesByCapability(KernelCapability capability) {
        return modules.values().stream()
            .filter(m -> m.getCapabilities().contains(capability))
            .collect(Collectors.toList());
    }

    @Override
    public List<KernelModule> getDependentModules(String moduleId) {
        return modules.values().stream()
            .filter(m -> m.getDependencies().stream()
                .anyMatch(d -> d.getDependencyId().equals(moduleId)))
            .collect(Collectors.toList());
    }

    @Override
    public List<KernelModule> resolveDependencies(KernelModule module) {
        // Topological sort for dependency resolution
        Set<String> visited = new HashSet<>();
        List<KernelModule> resolved = new ArrayList<>();

        resolveDependenciesRecursive(module, visited, resolved);

        resolved.removeIf(candidate -> candidate.getModuleId().equals(module.getModuleId()));

        return resolved;
    }

    private void resolveDependenciesRecursive(KernelModule module, Set<String> visited,
                                                 List<KernelModule> resolved) {
        if (visited.contains(module.getModuleId())) {
            return;
        }

        visited.add(module.getModuleId());

        // Resolve dependencies first
        for (KernelDependency dep : module.getDependencies()) {
            if (dep.getType() == KernelDependency.DependencyType.MODULE) {
                KernelModule depModule = modules.get(dep.getDependencyId());
                if (depModule != null) {
                    resolveDependenciesRecursive(depModule, visited, resolved);
                }
            }
        }

        resolved.add(module);
    }

    @Override
    public boolean validateDependencies(KernelModule module) {
        return getDependencyValidationErrors(module).isEmpty();
    }

    @Override
    public List<String> getDependencyValidationErrors(KernelModule module) {
        List<String> errors = new ArrayList<>();

        for (KernelDependency dep : module.getDependencies()) {
            switch (dep.getType()) {
                case MODULE -> {
                    if (!dep.isOptional() && !modules.containsKey(dep.getDependencyId())) {
                        errors.add("Missing required module: " + dep.getDependencyId());
                    }
                }
                case CAPABILITY -> {
                    boolean hasCapability = capabilities.stream()
                        .anyMatch(c -> c.getCapabilityId().equals(dep.getDependencyId()));
                    if (!dep.isOptional() && !hasCapability) {
                        errors.add("Missing required capability: " + dep.getDependencyId());
                    }
                }
                case EXTERNAL_SERVICE -> {
                    // External service validation is optional
                    if (!dep.isOptional()) {
                        errors.add("Required external service not available: " + dep.getDependencyId());
                    }
                }
                case FEATURE, PLUGIN, EXTENSION, CONFIGURATION -> {
                    // These dependency types are validated separately or are soft dependencies
                    if (!dep.isOptional()) {
                        errors.add("Required " + dep.getType() + " not available: " + dep.getDependencyId());
                    }
                }
            }
        }

        return errors;
    }

    @Override
    public Promise<Void> startAllModules() {
        List<KernelModule> ordered = topologicalSort(modules.values());
        List<KernelModule> startedModules = new CopyOnWriteArrayList<>();
        Promise<Void> chain = Promise.complete();

        for (KernelModule module : ordered) {
            chain = chain.then(() -> module.start()
                .map($ -> {
                    startedModules.add(module);
                    return null;
                }));
        }

        return chain.then(
            result -> Promise.complete(),
            exception -> rollbackStartedModules(startedModules).then(
                ignored -> Promise.ofException(exception),
                rollbackException -> {
                    rollbackException.addSuppressed(exception);
                    return Promise.ofException(rollbackException);
                })
        );
    }

    @Override
    public Promise<Void> stopAllModules() {
        List<KernelModule> ordered = topologicalSort(modules.values());
        Collections.reverse(ordered);
        Promise<Void> chain = Promise.complete();

        for (KernelModule module : ordered) {
            chain = chain.then(module::stop);
        }

        return chain;
    }

    @Override
    public boolean isModuleRegistered(String moduleId) {
        return modules.containsKey(moduleId);
    }

    @Override
    public boolean isCapabilityAvailable(String capabilityId) {
        return capabilities.stream()
            .anyMatch(c -> c.getCapabilityId().equals(capabilityId));
    }

    // ==================== Dependency Registration ====================

    /**
     * Registers a dependency instance.
     *
     * @param type the dependency type
     * @param instance the dependency instance
     * @param <T> the dependency type
     */
    public <T> void registerDependency(Class<T> type, T instance) {
        dependencies.put(type, instance);
    }

    /**
     * Registers a named dependency.
     *
     * @param name the dependency name
     * @param instance the dependency instance
     */
    public void registerDependency(String name, Object instance) {
        dependencyByName.put(name, instance.getClass());
        dependencies.put(instance.getClass(), instance);
    }

    /**
     * Gets a dependency by name and type.
     *
     * @param name the dependency name
     * @param type the expected type
     * @param <T> the dependency type
     * @return the dependency instance
     * @throws IllegalStateException if not found or type mismatch
     */
    @SuppressWarnings("unchecked")
    public <T> T getDependency(String name, Class<T> type) {
        Class<?> registeredType = dependencyByName.get(name);
        if (registeredType == null) {
            throw new IllegalStateException("Dependency not found: " + name);
        }
        if (!type.isAssignableFrom(registeredType)) {
            throw new IllegalStateException("Dependency type mismatch for '" + name +
                "': expected " + type.getName() + " but was " + registeredType.getName());
        }
        Object dep = dependencies.get(registeredType);
        if (dep == null) {
            throw new IllegalStateException("Dependency not found: " + name);
        }
        return (T) dep;
    }

    /**
     * Gets a dependency by type.
     *
     * @param type the dependency type
     * @param <T> the dependency type
     * @return the dependency instance
     * @throws IllegalStateException if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getDependency(Class<T> type) {
        Object dep = dependencies.get(type);
        if (dep == null) {
            throw new IllegalStateException("Dependency not found: " + type.getName());
        }
        return (T) dep;
    }

    /**
     * Gets an optional dependency by type.
     *
     * @param type the dependency type
     * @param <T> the dependency type
     * @return optional containing dependency if present
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOptionalDependency(Class<T> type) {
        return Optional.ofNullable((T) dependencies.get(type));
    }

    /**
     * Checks if a dependency is registered.
     *
     * @param type the dependency type
     * @return true if registered
     */
    public boolean hasDependency(Class<?> type) {
        return dependencies.containsKey(type);
    }

    // ==================== Private Methods ====================

    private List<KernelModule> topologicalSort(Collection<KernelModule> modulesToSort) {
        // Kahn's algorithm for topological sorting
        Map<String, Set<String>> graph = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        // Initialize graph
        for (KernelModule module : modulesToSort) {
            String id = module.getModuleId();
            graph.put(id, new HashSet<>());
            inDegree.put(id, 0);
        }

        // Build dependency graph
        for (KernelModule module : modulesToSort) {
            String id = module.getModuleId();
            for (KernelDependency dep : module.getDependencies()) {
                if (dep.getType() == KernelDependency.DependencyType.MODULE &&
                    graph.containsKey(dep.getDependencyId())) {
                    graph.get(dep.getDependencyId()).add(id);
                    inDegree.put(id, inDegree.get(id) + 1);
                }
            }
        }

        // Find nodes with no dependencies
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<KernelModule> result = new ArrayList<>();
        Map<String, KernelModule> moduleMap = modulesToSort.stream()
            .collect(Collectors.toMap(KernelModule::getModuleId, m -> m));

        while (!queue.isEmpty()) {
            String id = queue.poll();
            result.add(moduleMap.get(id));

            for (String dependent : graph.get(id)) {
                inDegree.put(dependent, inDegree.get(dependent) - 1);
                if (inDegree.get(dependent) == 0) {
                    queue.add(dependent);
                }
            }
        }

        return result;
    }

    private Promise<Void> rollbackStartedModules(List<KernelModule> startedModules) {
        List<KernelModule> rollbackOrder = new ArrayList<>(startedModules);
        Collections.reverse(rollbackOrder);

        Promise<Void> chain = Promise.complete();
        for (KernelModule module : rollbackOrder) {
            chain = chain.then(module::stop);
        }

        return chain;
    }

    // ==================== Health Status ====================

    /**
     * Gets aggregate health status of all registered modules.
     *
     * @return aggregate health status
     */
    public HealthStatus getAggregateHealthStatus() {
        HealthStatus.Builder builder = HealthStatus.builder()
            .withStatus(HealthStatus.Status.HEALTHY)
            .withMessage("Kernel registry operational");

        for (KernelModule module : modules.values()) {
            HealthStatus status = module.getHealthStatus();
            builder.withCheck(module.getModuleId(),
                status.getStatus(),
                status.getMessage(),
                0);

            if (status.getStatus() == HealthStatus.Status.UNHEALTHY) {
                builder.withStatus(HealthStatus.Status.DEGRADED);
            }
        }

        return builder.build();
    }
}
