package com.ghatana.datacloud.spi;

import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.RecordType;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for storage plugins.
 *
 * <p>
 * <b>Purpose</b><br>
 * Central registry for all storage plugin implementations. Provides:
 * <ul>
 * <li>Plugin registration and discovery</li>
 * <li>Plugin selection by ID or capability</li>
 * <li>Default plugin configuration</li>
 * <li>Plugin lifecycle management</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Register plugins
 * StoragePluginRegistry registry = StoragePluginRegistry.getInstance();
 * registry.register(new PostgreSQLStoragePlugin());
 * registry.register(new CassandraStoragePlugin());
 *
 * // Get plugin by ID
 * StoragePlugin<?> plugin = registry.getPlugin("postgresql")
 *     .orElseThrow(() -> new IllegalStateException("Plugin not found"));
 *
 * // Get default plugin for record type
 * StoragePlugin<?> eventPlugin = registry.getDefaultPlugin(RecordType.EVENT)
 *     .orElseThrow(() -> new IllegalStateException("No default for EVENT"));
 *
 * // Find all plugins supporting a record type
 * List<StoragePlugin<?>> entityPlugins = registry.findPluginsFor(RecordType.ENTITY);
 * }</pre>
 *
 * @see StoragePlugin
 * @doc.type class
 * @doc.purpose Plugin registry
 * @doc.layer core
 * @doc.pattern Registry
 */
public class StoragePluginRegistry {

    private static final StoragePluginRegistry INSTANCE = new StoragePluginRegistry();

    /**
     * All registered plugins by ID.
     */
    private final Map<String, StoragePlugin<? extends DataRecord>> plugins = new ConcurrentHashMap<>();

    /**
     * Default plugin for each record type.
     */
    private final Map<RecordType, String> defaultPlugins = new ConcurrentHashMap<>();

    /**
     * Plugin initialization configs.
     */
    private final Map<String, Map<String, Object>> pluginConfigs = new ConcurrentHashMap<>();

    private StoragePluginRegistry() {
    }

    /**
     * Gets the singleton instance.
     *
     * @return Registry instance
     */
    public static StoragePluginRegistry getInstance() {
        return INSTANCE;
    }

    // ==================== Registration ====================
    /**
     * Registers a storage plugin.
     *
     * @param plugin Plugin to register
     * @return This registry for chaining
     * @throws IllegalArgumentException if plugin with same ID already
     * registered
     */
    public StoragePluginRegistry register(StoragePlugin<? extends DataRecord> plugin) {
        Objects.requireNonNull(plugin, "Plugin cannot be null");
        String pluginId = plugin.getPluginId();

        if (plugins.containsKey(pluginId)) {
            throw new IllegalArgumentException("Plugin already registered: " + pluginId);
        }

        plugins.put(pluginId, plugin);
        return this;
    }

    /**
     * Registers a plugin with configuration.
     *
     * @param plugin Plugin to register
     * @param config Plugin configuration
     * @return This registry for chaining
     */
    public StoragePluginRegistry register(StoragePlugin<? extends DataRecord> plugin, Map<String, Object> config) {
        register(plugin);
        pluginConfigs.put(plugin.getPluginId(), new HashMap<>(config));
        return this;
    }

    /**
     * Unregisters a plugin by ID.
     *
     * @param pluginId Plugin ID to unregister
     * @return Promise completing when plugin is shutdown and removed
     */
    public Promise<Void> unregister(String pluginId) {
        StoragePlugin<? extends DataRecord> plugin = plugins.remove(pluginId);
        if (plugin != null) {
            pluginConfigs.remove(pluginId);
            // Remove from defaults if it was default for any type
            defaultPlugins.values().removeIf(id -> id.equals(pluginId));
            return plugin.shutdown();
        }
        return Promise.complete();
    }

    // ==================== Retrieval ====================
    /**
     * Gets a plugin by ID.
     *
     * @param pluginId Plugin ID
     * @return Plugin if found
     */
    public Optional<StoragePlugin<? extends DataRecord>> getPlugin(String pluginId) {
        return Optional.ofNullable(plugins.get(pluginId));
    }

    /**
     * Gets a plugin by ID, typed.
     *
     * @param pluginId Plugin ID
     * @param recordClass Expected record class
     * @return Plugin if found and matches type
     */
    @SuppressWarnings("unchecked")
    public <R extends DataRecord> Optional<StoragePlugin<R>> getPlugin(String pluginId, Class<R> recordClass) {
        return Optional.ofNullable((StoragePlugin<R>) plugins.get(pluginId));
    }

    /**
     * Gets the default plugin for a record type.
     *
     * @param recordType Record type
     * @return Default plugin if configured
     */
    public Optional<StoragePlugin<? extends DataRecord>> getDefaultPlugin(RecordType recordType) {
        String defaultId = defaultPlugins.get(recordType);
        if (defaultId != null) {
            return getPlugin(defaultId);
        }
        // Fall back to first plugin supporting this type
        return findPluginsFor(recordType).stream().findFirst();
    }

    /**
     * Finds all plugins supporting a record type.
     *
     * @param recordType Record type
     * @return List of supporting plugins
     */
    public List<StoragePlugin<? extends DataRecord>> findPluginsFor(RecordType recordType) {
        return plugins.values().stream()
                .filter(p -> p.supportsRecordType(recordType))
                .toList();
    }

    /**
     * Gets all registered plugins.
     *
     * @return Unmodifiable collection of all plugins
     */
    public java.util.Collection<StoragePlugin<? extends DataRecord>> getAllPlugins() {
        return Collections.unmodifiableCollection(plugins.values());
    }

    /**
     * Gets all registered plugin IDs.
     *
     * @return Set of plugin IDs
     */
    public Set<String> getPluginIds() {
        return Collections.unmodifiableSet(plugins.keySet());
    }

    // ==================== Configuration ====================
    /**
     * Sets the default plugin for a record type.
     *
     * @param recordType Record type
     * @param pluginId Plugin ID
     * @return This registry for chaining
     * @throws IllegalArgumentException if plugin doesn't support the record
     * type
     */
    public StoragePluginRegistry setDefaultPlugin(RecordType recordType, String pluginId) {
        StoragePlugin<? extends DataRecord> plugin = plugins.get(pluginId);
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin not found: " + pluginId);
        }
        if (!plugin.supportsRecordType(recordType)) {
            throw new IllegalArgumentException(
                    "Plugin " + pluginId + " does not support record type " + recordType);
        }
        defaultPlugins.put(recordType, pluginId);
        return this;
    }

    /**
     * Gets configuration for a plugin.
     *
     * @param pluginId Plugin ID
     * @return Configuration map (empty if not configured)
     */
    public Map<String, Object> getPluginConfig(String pluginId) {
        return pluginConfigs.getOrDefault(pluginId, Map.of());
    }

    /**
     * Sets configuration for a plugin.
     *
     * @param pluginId Plugin ID
     * @param config Configuration map
     * @return This registry for chaining
     */
    public StoragePluginRegistry setPluginConfig(String pluginId, Map<String, Object> config) {
        pluginConfigs.put(pluginId, new HashMap<>(config));
        return this;
    }

    // ==================== Lifecycle ====================
    /**
     * Initializes all registered plugins.
     *
     * @return Promise completing when all plugins are initialized
     */
    public Promise<Void> initializeAll() {
        List<Promise<Void>> promises = plugins.values().stream()
                .map(plugin -> {
                    Map<String, Object> config = pluginConfigs.getOrDefault(plugin.getPluginId(), Map.of());
                    return plugin.initialize(config);
                })
                .toList();

        return Promises.all(promises).toVoid();
    }

    /**
     * Initializes a specific plugin.
     *
     * @param pluginId Plugin ID
     * @return Promise completing when plugin is initialized
     */
    public Promise<Void> initialize(String pluginId) {
        StoragePlugin<? extends DataRecord> plugin = plugins.get(pluginId);
        if (plugin == null) {
            return Promise.ofException(new IllegalArgumentException("Plugin not found: " + pluginId));
        }
        Map<String, Object> config = pluginConfigs.getOrDefault(pluginId, Map.of());
        return plugin.initialize(config);
    }

    /**
     * Shuts down all registered plugins.
     *
     * @return Promise completing when all plugins are shut down
     */
    public Promise<Void> shutdownAll() {
        List<Promise<Void>> promises = plugins.values().stream()
                .map(StoragePlugin::shutdown)
                .toList();

        return Promises.all(promises).toVoid();
    }

    /**
     * Performs health check on all plugins.
     *
     * @return Promise with map of plugin ID to health status
     */
    public Promise<Map<String, StoragePlugin.HealthStatus>> healthCheckAll() {
        Map<String, Promise<StoragePlugin.HealthStatus>> healthPromises = new HashMap<>();

        for (var entry : plugins.entrySet()) {
            healthPromises.put(entry.getKey(), entry.getValue().healthCheck());
        }

        Map<String, StoragePlugin.HealthStatus> results = new ConcurrentHashMap<>();

        List<Promise<Void>> allPromises = healthPromises.entrySet().stream()
                .map(entry -> entry.getValue()
                .map(status -> {
                    results.put(entry.getKey(), status);
                    return null;
                })
                .mapException(ex -> {
                    results.put(entry.getKey(),
                            StoragePlugin.HealthStatus.error("Health check failed: " + ex.getMessage()));
                    return null;
                })
                .toVoid())
                .toList();

        return Promises.all(allPromises).map($ -> results);
    }

    // ==================== Utility ====================
    /**
     * Clears all registered plugins (for testing).
     */
    public void clear() {
        plugins.clear();
        defaultPlugins.clear();
        pluginConfigs.clear();
    }

    /**
     * Checks if a plugin is registered.
     *
     * @param pluginId Plugin ID
     * @return true if registered
     */
    public boolean isRegistered(String pluginId) {
        return plugins.containsKey(pluginId);
    }

    /**
     * Gets the count of registered plugins.
     *
     * @return Plugin count
     */
    public int getPluginCount() {
        return plugins.size();
    }
}
