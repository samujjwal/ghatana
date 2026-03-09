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

/**
 * Base interface for all YAPPC plugins. Defines the plugin lifecycle and
 * metadata contract.
 *
 * @doc.type interface
 * @doc.purpose Base plugin interface for extensibility
 * @doc.layer platform
 * @doc.pattern Plugin SPI
 */
public interface YappcPlugin {

    /**
     * Returns the plugin metadata.
     *
     * @return plugin metadata
     */
    PluginMetadata getMetadata();

    /**
     * Initializes the plugin with the given context.
     *
     * @param context plugin initialization context
     * @throws PluginException if initialization fails
     */
    void initialize(PluginContext context) throws PluginException;

    /**
     * Performs health check on the plugin.
     *
     * @return health check result
     */
    PluginHealthResult healthCheck();

    /**
     * Shuts down the plugin and releases resources.
     *
     * @throws PluginException if shutdown fails
     */
    void shutdown() throws PluginException;

    /**
     * Returns the plugin's current state.
     *
     * @return plugin state
     */
    PluginState getState();
}
