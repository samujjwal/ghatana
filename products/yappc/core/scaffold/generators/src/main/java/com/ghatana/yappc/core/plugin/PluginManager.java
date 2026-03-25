/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages plugin lifecycle: loading, initialization, health checks, and
 * shutdown.
 *
 * @doc.type class
 * @doc.purpose Plugin lifecycle management
 * @doc.layer platform
 * @doc.pattern Manager/Facade
 */
public class PluginManager {

    private final PluginLoader loader;
    private final PluginRegistry registry;
    private final PluginEventBus eventBus;
    private final Map<String, PluginState> pluginStates = new ConcurrentHashMap<>();

    public PluginManager() {
        this.loader = new PluginLoader();
        this.registry = new PluginRegistry();
        this.eventBus = new PluginEventBus();
    }

    public PluginManager(PluginLoader loader, PluginRegistry registry, PluginEventBus eventBus) {
        this.loader = loader;
        this.registry = registry;
        this.eventBus = eventBus;
    }

    /**
     * Loads and initializes a plugin from a JAR file.
     *
     * @param jarPath path to plugin JAR
     * @param context initialization context
     * @return loaded plugin
     * @throws PluginException if loading or initialization fails
     */
    public YappcPlugin loadAndInitialize(Path jarPath, PluginContext context) throws PluginException {
        // Load plugin
        YappcPlugin plugin = loader.loadPlugin(jarPath);
        String pluginId = plugin.getMetadata().id();

        pluginStates.put(pluginId, PluginState.LOADED);
        eventBus.publish(PluginEvent.of(PluginEvent.PluginEventType.PLUGIN_LOADED, pluginId));

        try {
            // Register plugin
            registry.register(plugin);

            // Initialize plugin
            plugin.initialize(context);
            pluginStates.put(pluginId, PluginState.INITIALIZED);
            eventBus.publish(PluginEvent.of(PluginEvent.PluginEventType.PLUGIN_INITIALIZED, pluginId));

            // Activate plugin
            pluginStates.put(pluginId, PluginState.ACTIVE);

            return plugin;

        } catch (Exception e) {
            pluginStates.put(pluginId, PluginState.ERROR);
            eventBus.publish(PluginEvent.of(
                    PluginEvent.PluginEventType.PLUGIN_ERROR,
                    pluginId,
                    Map.of("error", e.getMessage())));
            throw new PluginException("Failed to initialize plugin: " + pluginId, e);
        }
    }

    /**
     * Loads and initializes all plugins from a directory.
     *
     * @param pluginsDir directory containing plugin JARs
     * @param context    initialization context
     * @return list of loaded plugins
     * @throws PluginException if loading fails
     */
    public List<YappcPlugin> loadAndInitializeAll(Path pluginsDir, PluginContext context)
            throws PluginException {
        List<YappcPlugin> plugins = loader.loadPluginsFromDirectory(pluginsDir);
        List<YappcPlugin> initialized = new ArrayList<>();
        List<PluginException> errors = new ArrayList<>();

        for (YappcPlugin plugin : plugins) {
            String pluginId = plugin.getMetadata().id();
            pluginStates.put(pluginId, PluginState.LOADED);
            eventBus.publish(PluginEvent.of(PluginEvent.PluginEventType.PLUGIN_LOADED, pluginId));

            try {
                registry.register(plugin);
                plugin.initialize(context);
                pluginStates.put(pluginId, PluginState.INITIALIZED);
                eventBus.publish(PluginEvent.of(PluginEvent.PluginEventType.PLUGIN_INITIALIZED, pluginId));

                pluginStates.put(pluginId, PluginState.ACTIVE);
                initialized.add(plugin);

            } catch (Exception e) {
                pluginStates.put(pluginId, PluginState.ERROR);
                eventBus.publish(PluginEvent.of(
                        PluginEvent.PluginEventType.PLUGIN_ERROR,
                        pluginId,
                        Map.of("error", e.getMessage())));
                errors.add(new PluginException("Failed to initialize plugin: " + pluginId, e));
            }
        }

        if (!errors.isEmpty() && initialized.isEmpty()) {
            throw new PluginException(
                    "Failed to initialize any plugins. First error: " + errors.get(0).getMessage());
        }

        return initialized;
    }

    /**
     * Performs health check on a plugin.
     *
     * @param pluginId plugin ID
     * @return health check result
     * @throws PluginException if plugin not found
     */
    public PluginHealthResult healthCheck(String pluginId) throws PluginException {
        YappcPlugin plugin = registry.getPlugin(pluginId)
                .orElseThrow(() -> new PluginException("Plugin not found: " + pluginId));

        return plugin.healthCheck();
    }

    /**
     * Performs health check on all plugins.
     *
     * @return map of plugin ID to health result
     */
    public Map<String, PluginHealthResult> healthCheckAll() {
        Map<String, PluginHealthResult> results = new ConcurrentHashMap<>();
        for (YappcPlugin plugin : registry.getAllPlugins()) {
            String pluginId = plugin.getMetadata().id();
            try {
                results.put(pluginId, plugin.healthCheck());
            } catch (Exception e) {
                results.put(pluginId, PluginHealthResult.createUnhealthy(
                        "Health check failed: " + e.getMessage()));
            }
        }
        return results;
    }

    /**
     * Shuts down a plugin.
     *
     * @param pluginId plugin ID
     * @throws PluginException if shutdown fails
     */
    public void shutdown(String pluginId) throws PluginException {
        YappcPlugin plugin = registry.getPlugin(pluginId)
                .orElseThrow(() -> new PluginException("Plugin not found: " + pluginId));

        try {
            plugin.shutdown();
            pluginStates.put(pluginId, PluginState.SHUTDOWN);
            eventBus.publish(PluginEvent.of(PluginEvent.PluginEventType.PLUGIN_SHUTDOWN, pluginId));
            registry.unregister(pluginId);

        } catch (Exception e) {
            pluginStates.put(pluginId, PluginState.ERROR);
            throw new PluginException("Failed to shutdown plugin: " + pluginId, e);
        }
    }

    /**
     * Shuts down all plugins.
     */
    public void shutdownAll() {
        List<YappcPlugin> plugins = registry.getAllPlugins();
        for (YappcPlugin plugin : plugins) {
            String pluginId = plugin.getMetadata().id();
            try {
                shutdown(pluginId);
            } catch (PluginException e) {
                // Log but continue
            }
        }
        loader.unloadAll();
        registry.clear();
        pluginStates.clear();
    }

    /**
     * Gets the current state of a plugin.
     *
     * @param pluginId plugin ID
     * @return plugin state
     */
    public PluginState getPluginState(String pluginId) {
        return pluginStates.getOrDefault(pluginId, PluginState.UNLOADED);
    }

    /**
     * Gets the plugin registry.
     *
     * @return plugin registry
     */
    public PluginRegistry getRegistry() {
        return registry;
    }

    /**
     * Gets the event bus.
     *
     * @return event bus
     */
    public PluginEventBus getEventBus() {
        return eventBus;
    }
}
