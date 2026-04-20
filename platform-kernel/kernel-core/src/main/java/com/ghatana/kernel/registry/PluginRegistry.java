package com.ghatana.kernel.registry;

import com.ghatana.kernel.annotation.KernelInternal;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.plugin.KernelPlugin;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Plugin registry for dynamic plugin management.
 *
 * <p>This registry manages kernel plugins without creating coupling
 * between the kernel and specific products.</p>
 *
 * <p><b>Internal helper registry.</b> Per KERNEL_CANONICALIZATION_DECISIONS.md (Decision D4),
 * {@link KernelRegistry} is the only public root registry contract. This class is an
 * internal implementation helper. External consumers should prefer KernelRegistry for
 * plugin discovery. This class may be refactored into KernelRegistryImpl in a future release.</p>
 *
 * @doc.type class
 * @doc.purpose Internal plugin sub-registry behind KernelRegistry
 * @doc.layer core
 * @doc.pattern Registry
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@KernelInternal("Use KernelRegistry for plugin discovery")
public class PluginRegistry {
    private final Map<String, KernelPlugin> plugins = new ConcurrentHashMap<>();
    private final Map<String, Set<KernelCapability>> capabilitiesByPlugin = new ConcurrentHashMap<>();
    /** Plugin IDs that have been explicitly disabled at runtime. */
    private final Set<String> disabledPlugins = ConcurrentHashMap.newKeySet();
    private final CapabilityRegistry capabilityRegistry;

    public PluginRegistry(CapabilityRegistry capabilityRegistry) {
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry);
    }

    /**
     * Register a kernel plugin dynamically.
     *
     * @param plugin the plugin to register
     * @throws IllegalStateException if plugin is already registered
     */
    public void registerPlugin(KernelPlugin plugin) {
        String pluginId = plugin.getModuleId();

        if (plugins.containsKey(pluginId)) {
            throw new IllegalStateException("Plugin already registered: " + pluginId);
        }

        validatePluginDependencies(plugin);

        plugins.put(pluginId, plugin);

        Set<KernelCapability> capabilities = plugin.getCapabilities();
        capabilitiesByPlugin.put(pluginId, capabilities);

        for (KernelCapability capability : capabilities) {
            capabilityRegistry.registerCapability(capability);
        }
    }

    /**
     * Get plugin by ID.
     *
     * @param pluginId the plugin identifier
     * @return optional plugin
     */
    public Optional<KernelPlugin> getPlugin(String pluginId) {
        return Optional.ofNullable(plugins.get(pluginId));
    }

    /**
     * Get all registered plugins.
     *
     * @return set of all plugins
     */
    public Set<KernelPlugin> getAllPlugins() {
        return new HashSet<>(plugins.values());
    }

    /**
     * Get capabilities for a plugin.
     *
     * @param pluginId the plugin identifier
     * @return set of capabilities
     */
    public Set<KernelCapability> getPluginCapabilities(String pluginId) {
        return capabilitiesByPlugin.getOrDefault(pluginId, Set.of());
    }

    /**
     * Find plugins by capability.
     *
     * @param capability the capability to search for
     * @return set of plugins that provide the capability
     */
    public Set<KernelPlugin> getPluginsByCapability(KernelCapability capability) {
        return plugins.values().stream()
            .filter(plugin -> plugin.getCapabilities().contains(capability))
            .collect(Collectors.toSet());
    }

    /**
     * Disables a registered plugin at runtime.
     *
     * <p>The plugin remains registered but will not be started by
     * {@link #startAllPlugins()} and will be stopped if it is currently running.
     * Capabilities contributed by the plugin remain visible in the
     * {@link CapabilityRegistry} to avoid breaking already-resolved dependency
     * references; callers should check {@link #isPluginEnabled(String)} when
     * routing requests at runtime.</p>
     *
     * @param pluginId the ID of the plugin to disable
     * @return Promise completing when the plugin has been stopped (no-op if not started)
     * @throws IllegalArgumentException if the plugin is not registered
     */
    public Promise<Void> disablePlugin(String pluginId) {
        if (!plugins.containsKey(pluginId)) {
            throw new IllegalArgumentException("Plugin not registered: " + pluginId);
        }
        disabledPlugins.add(pluginId);
        KernelPlugin plugin = plugins.get(pluginId);
        if (plugin == null) {
            return Promise.complete();
        }
        return plugin.stop().whenException(e ->
            System.err.println("Error stopping plugin " + pluginId + " during disable: " + e.getMessage()));
    }

    /**
     * Re-enables a previously disabled plugin.
     *
     * <p>The plugin is removed from the disabled set. Call {@code plugin.start()}
     * separately (or restart via {@link #startAllPlugins()}) to activate it.</p>
     *
     * @param pluginId the ID of the plugin to enable
     * @throws IllegalArgumentException if the plugin is not registered
     */
    public void enablePlugin(String pluginId) {
        if (!plugins.containsKey(pluginId)) {
            throw new IllegalArgumentException("Plugin not registered: " + pluginId);
        }
        disabledPlugins.remove(pluginId);
    }

    /**
     * Returns {@code true} if the plugin is registered and not disabled.
     *
     * @param pluginId the plugin identifier
     * @return whether the plugin is active
     */
    public boolean isPluginEnabled(String pluginId) {
        return plugins.containsKey(pluginId) && !disabledPlugins.contains(pluginId);
    }

    /**
     * Returns the set of plugin IDs that are currently disabled.
     *
     * @return unmodifiable view of disabled plugin IDs
     */
    public Set<String> getDisabledPluginIds() {
        return Collections.unmodifiableSet(disabledPlugins);
    }

    /**
     * Start all plugins.
     *
     * <p>Plugins in the disabled set are skipped silently.</p>
     *
     * @return Promise completing when all enabled plugins have started
     */
    public Promise<Void> startAllPlugins() {
        List<Promise<Void>> starts = plugins.entrySet().stream()
            .filter(entry -> !disabledPlugins.contains(entry.getKey()))
            .map(entry -> entry.getValue().start()
                .mapException(e -> new RuntimeException("Failed to start plugin: " + entry.getKey(), e)))
            .collect(Collectors.toList());
        return Promises.all(starts);
    }

    /**
     * Stop all plugins.
     *
     * @return Promise completing when all plugins have stopped
     */
    public Promise<Void> stopAllPlugins() {
        List<Promise<Void>> stops = plugins.values().stream()
            .map(plugin -> plugin.stop()
                .then($ -> Promise.complete(),
                      e -> {
                          System.err.println("Error stopping plugin " + plugin.getModuleId() + ": " + e.getMessage());
                          return Promise.complete();
                      }))
            .collect(Collectors.toList());
        return Promises.all(stops);
    }

    /**
     * Unregister a plugin.
     *
     * @param pluginId the plugin identifier
     * @return Promise completing when the plugin has been unregistered
     */
    public Promise<Void> unregisterPlugin(String pluginId) {
        KernelPlugin plugin = plugins.get(pluginId);
        if (plugin == null) {
            return Promise.complete();
        }
        return plugin.stop()
            .then($ -> plugin.uninstall())
            .whenComplete(($, e) -> {
                plugins.remove(pluginId);
                capabilitiesByPlugin.remove(pluginId);
            });
    }

    /**
     * Validate plugin dependencies.
     *
     * @param plugin the plugin to validate
     * @throws IllegalStateException if dependencies are not satisfied
     */
    private void validatePluginDependencies(KernelPlugin plugin) {
        for (KernelDependency dependency : plugin.getDependencies()) {
            if (dependency.getType() == KernelDependency.DependencyType.CAPABILITY) {
                if (!capabilityRegistry.getCapability(dependency.getDependencyId()).isPresent()) {
                    throw new IllegalStateException(
                        "Plugin " + plugin.getModuleId() + " requires capability: " + dependency.getDependencyId());
                }
            }
        }
    }

    /**
     * Get plugin registry statistics.
     *
     * @return statistics map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_plugins", plugins.size());
        stats.put("total_capabilities", capabilitiesByPlugin.values().stream()
            .mapToInt(Set::size).sum());
        stats.put("plugins_by_id", plugins.keySet().stream()
            .collect(Collectors.toMap(id -> id, id -> plugins.get(id).getVersion())));
        return stats;
    }
}
