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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for managing loaded plugins.
 *
 * @doc.type class
 * @doc.purpose Plugin registration and lookup
 * @doc.layer platform
 * @doc.pattern Registry
 */
public class PluginRegistry {

    private final Map<String, YappcPlugin> pluginsById = new ConcurrentHashMap<>();
    private final Map<PluginCapability, List<YappcPlugin>> pluginsByCapability = new ConcurrentHashMap<>();

    /**
     * Registers a plugin.
     *
     * @param plugin plugin to register
     * @throws PluginException if registration fails
     */
    public void register(YappcPlugin plugin) throws PluginException {
        PluginMetadata metadata = plugin.getMetadata();
        String id = metadata.id();

        if (pluginsById.containsKey(id)) {
            throw new PluginException("Plugin already registered: " + id);
        }

        pluginsById.put(id, plugin);

        // Index by capabilities
        for (PluginCapability capability : metadata.capabilities()) {
            pluginsByCapability.computeIfAbsent(capability, k -> new ArrayList<>())
                    .add(plugin);
        }
    }

    /**
     * Unregisters a plugin.
     *
     * @param pluginId plugin ID
     * @return true if plugin was unregistered
     */
    public boolean unregister(String pluginId) {
        YappcPlugin plugin = pluginsById.remove(pluginId);
        if (plugin == null) {
            return false;
        }

        // Remove from capability index
        PluginMetadata metadata = plugin.getMetadata();
        for (PluginCapability capability : metadata.capabilities()) {
            List<YappcPlugin> plugins = pluginsByCapability.get(capability);
            if (plugins != null) {
                plugins.remove(plugin);
                if (plugins.isEmpty()) {
                    pluginsByCapability.remove(capability);
                }
            }
        }

        return true;
    }

    /**
     * Gets a plugin by ID.
     *
     * @param pluginId plugin ID
     * @return plugin or empty
     */
    public Optional<YappcPlugin> getPlugin(String pluginId) {
        return Optional.ofNullable(pluginsById.get(pluginId));
    }

    /**
     * Gets all registered plugins.
     *
     * @return list of all plugins
     */
    public List<YappcPlugin> getAllPlugins() {
        return new ArrayList<>(pluginsById.values());
    }

    /**
     * Gets plugins by capability.
     *
     * @param capability capability to filter by
     * @return list of plugins with the capability
     */
    public List<YappcPlugin> getPluginsByCapability(PluginCapability capability) {
        return new ArrayList<>(pluginsByCapability.getOrDefault(capability, List.of()));
    }

    /**
     * Gets plugins by language support.
     *
     * @param language language identifier
     * @return list of plugins supporting the language
     */
    public List<YappcPlugin> getPluginsByLanguage(String language) {
        return pluginsById.values().stream()
                .filter(p -> p.getMetadata().supportedLanguages().contains(language))
                .collect(Collectors.toList());
    }

    /**
     * Gets plugins by build system support.
     *
     * @param buildSystem build system identifier
     * @return list of plugins supporting the build system
     */
    public List<YappcPlugin> getPluginsByBuildSystem(String buildSystem) {
        return pluginsById.values().stream()
                .filter(p -> p.getMetadata().supportedBuildSystems().contains(buildSystem))
                .collect(Collectors.toList());
    }

    /**
     * Checks if a plugin is registered.
     *
     * @param pluginId plugin ID
     * @return true if registered
     */
    public boolean isRegistered(String pluginId) {
        return pluginsById.containsKey(pluginId);
    }

    /**
     * Gets the count of registered plugins.
     *
     * @return plugin count
     */
    public int getPluginCount() {
        return pluginsById.size();
    }

    /**
     * Clears all registered plugins.
     */
    public void clear() {
        pluginsById.clear();
        pluginsByCapability.clear();
    }

    /**
     * Gets plugin metadata for all registered plugins.
     *
     * @return list of plugin metadata
     */
    public List<PluginMetadata> getAllMetadata() {
        return pluginsById.values().stream()
                .map(YappcPlugin::getMetadata)
                .collect(Collectors.toList());
    }
}
