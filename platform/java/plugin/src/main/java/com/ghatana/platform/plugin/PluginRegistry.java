package com.ghatana.platform.plugin;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central registry for managing plugins.
 * <p>
 * This is a DI-friendly, non-singleton registry that manages the full lifecycle
 * of registered plugins. It replaces the singleton-based data-cloud PluginRegistry
 * and the event-cloud PluginRegistry interface.
 * <p>
 * Features:
 * <ul>
 *   <li>Thread-safe plugin registration/deregistration</li>
 *   <li>Lookup by ID, type, capability (object or string-based)</li>
 *   <li>Lifecycle management: initialize → start → stop → shutdown</li>
 *   <li>Aggregated health checking</li>
 *   <li>ServiceLoader-based discovery via {@link PluginProvider}</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Plugin management
 * @doc.layer core
 */
public class PluginRegistry {

    private final Map<String, Plugin> plugins = new ConcurrentHashMap<>();
    private final Map<PluginType, Set<String>> pluginsByType = new ConcurrentHashMap<>();

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Registers a plugin. Throws if a plugin with the same ID is already registered.
     *
     * @param plugin the plugin to register
     * @throws IllegalArgumentException if a plugin with the same ID already exists
     */
    public void register(@NotNull Plugin plugin) {
        Objects.requireNonNull(plugin, "Plugin must not be null");
        PluginMetadata metadata = plugin.metadata();
        Objects.requireNonNull(metadata, "Plugin metadata must not be null");
        String id = metadata.id();
        Objects.requireNonNull(id, "Plugin id must not be null");

        Plugin existing = plugins.putIfAbsent(id, plugin);
        if (existing != null) {
            throw new IllegalArgumentException("Plugin already registered: " + id);
        }
        pluginsByType.computeIfAbsent(metadata.type(), k -> ConcurrentHashMap.newKeySet()).add(id);
    }

    /**
     * Unregisters a plugin by ID.
     *
     * @param pluginId the ID of the plugin to unregister
     * @return the unregistered plugin, or empty if not found
     */
    @NotNull
    public Optional<Plugin> unregister(@NotNull String pluginId) {
        Plugin removed = plugins.remove(pluginId);
        if (removed != null) {
            pluginsByType.values().forEach(ids -> ids.remove(pluginId));
        }
        return Optional.ofNullable(removed);
    }

    /**
     * Returns true if a plugin with the given ID is registered.
     */
    public boolean isRegistered(@NotNull String pluginId) {
        return plugins.containsKey(pluginId);
    }

    /**
     * Returns the number of registered plugins.
     */
    public int size() {
        return plugins.size();
    }

    /**
     * Removes all registered plugins.
     */
    public void clear() {
        plugins.clear();
        pluginsByType.clear();
    }

    // =========================================================================
    // Lookup
    // =========================================================================

    /**
     * Retrieves a plugin by ID.
     */
    @NotNull
    public Optional<Plugin> getPlugin(@NotNull String pluginId) {
        return Optional.ofNullable(plugins.get(pluginId));
    }

    /**
     * Retrieves a plugin by ID and casts to the expected type.
     *
     * @param pluginId the plugin ID
     * @param type     the expected plugin type
     * @return the plugin cast to the expected type, or empty
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public <T extends Plugin> Optional<T> getPlugin(@NotNull String pluginId, @NotNull Class<T> type) {
        return getPlugin(pluginId).filter(type::isInstance).map(p -> (T) p);
    }

    /**
     * Returns all registered plugins as an unmodifiable collection.
     */
    @NotNull
    public Collection<Plugin> getAllPlugins() {
        return Collections.unmodifiableCollection(plugins.values());
    }

    /**
     * Finds all plugins with a specific {@link PluginCapability} object capability.
     */
    @NotNull
    public List<Plugin> findByCapability(@NotNull Class<? extends PluginCapability> capability) {
        return plugins.values().stream()
            .filter(p -> p.getCapability(capability).isPresent())
            .collect(Collectors.toList());
    }

    /**
     * Finds all plugins that declare a string-based capability in their metadata.
     */
    @NotNull
    public List<Plugin> findByStringCapability(@NotNull String capability) {
        return plugins.values().stream()
            .filter(p -> p.metadata().hasCapability(capability))
            .collect(Collectors.toList());
    }

    /**
     * Finds all plugins of a specific {@link PluginType}.
     */
    @NotNull
    public List<Plugin> findByType(@NotNull PluginType type) {
        Set<String> ids = pluginsByType.getOrDefault(type, Set.of());
        return ids.stream()
            .map(plugins::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Finds all plugins matching the given predicate.
     */
    @NotNull
    public List<Plugin> find(@NotNull java.util.function.Predicate<Plugin> predicate) {
        return plugins.values().stream()
            .filter(predicate)
            .collect(Collectors.toList());
    }

    // =========================================================================
    // Lifecycle Management
    // =========================================================================

    /**
     * Initializes all registered plugins with the given context.
     */
    @NotNull
    public Promise<Void> initializeAll(@NotNull PluginContext context) {
        return Promises.all(plugins.values().stream()
            .map(p -> p.initialize(context))
            .collect(Collectors.toList()));
    }

    /**
     * Starts all registered plugins.
     */
    @NotNull
    public Promise<Void> startAll() {
        return Promises.all(plugins.values().stream()
            .map(Plugin::start)
            .collect(Collectors.toList()));
    }

    /**
     * Stops all registered plugins.
     */
    @NotNull
    public Promise<Void> stopAll() {
        return Promises.all(plugins.values().stream()
            .map(Plugin::stop)
            .collect(Collectors.toList()));
    }

    /**
     * Shuts down all registered plugins (stop + release resources).
     */
    @NotNull
    public Promise<Void> shutdownAll() {
        return Promises.all(plugins.values().stream()
            .map(Plugin::shutdown)
            .collect(Collectors.toList()));
    }

    // =========================================================================
    // Health Checking
    // =========================================================================

    /**
     * Checks health of all registered plugins and returns a map of plugin ID → status.
     */
    @NotNull
    public Promise<Map<String, HealthStatus>> healthCheckAll() {
        if (plugins.isEmpty()) {
            return Promise.of(Map.of());
        }
        return Promises.toList(plugins.values().stream()
                .map(p -> p.healthCheck()
                    .map(status -> Map.entry(p.metadata().id(), status))
                    .mapException(ex -> new RuntimeException("Health check failed for " + p.metadata().id(), ex)))
                .collect(Collectors.toList()))
            .map(entries -> entries.stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    /**
     * Returns an aggregated health status for the entire registry.
     */
    @NotNull
    public Promise<HealthStatus> aggregateHealth() {
        return healthCheckAll().map(results -> {
            if (results.isEmpty()) {
                return HealthStatus.ok("No plugins registered");
            }
            boolean allHealthy = results.values().stream().allMatch(HealthStatus::healthy);
            long unhealthyCount = results.values().stream()
                .filter(s -> !s.healthy()).count();
            Map<String, Object> details = new LinkedHashMap<>(results);
            if (allHealthy) {
                return HealthStatus.ok(results.size() + " plugins healthy", details);
            } else {
                return HealthStatus.unhealthy(
                    unhealthyCount + "/" + results.size() + " plugins unhealthy",
                    details
                );
            }
        });
    }

    // =========================================================================
    // Discovery
    // =========================================================================

    /**
     * Discovers and registers plugins using {@link java.util.ServiceLoader}.
     * <p>
     * Loads all {@link PluginProvider} implementations from the classpath,
     * filters to enabled ones, sorts by priority, and registers the created plugins.
     *
     * @return the number of plugins discovered and registered
     */
    public int discoverPlugins() {
        ServiceLoader<PluginProvider> loader = ServiceLoader.load(PluginProvider.class);
        List<PluginProvider> providers = new ArrayList<>();
        for (PluginProvider provider : loader) {
            if (provider.isEnabled()) {
                providers.add(provider);
            }
        }
        providers.sort(Comparator.comparingInt(PluginProvider::priority));

        int count = 0;
        for (PluginProvider provider : providers) {
            try {
                Plugin plugin = provider.createPlugin();
                register(plugin);
                count++;
            } catch (Exception e) {
                // Log but continue discovering other plugins
                System.err.println("Failed to create plugin from provider " +
                    provider.getMetadata().id() + ": " + e.getMessage());
            }
        }
        return count;
    }
}
