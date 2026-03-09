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

package com.ghatana.yappc.framework.core;

import com.ghatana.yappc.framework.api.plugin.BuildGeneratorPlugin;
import com.ghatana.yappc.framework.core.plugin.PluginManager;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap entry-point for the YAPPC framework.
 *
 * <p>Initialises the {@link PluginManager}, triggers classpath-based plugin
 * discovery, and provides convenience accessors used by integration tests and
 * the runtime launcher.</p>
 *
 * @doc.type class
 * @doc.purpose Framework initialisation and lifecycle management
 * @doc.layer product
 * @doc.pattern Bootstrap
 */
public class FrameworkBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(FrameworkBootstrap.class);

    private final PluginManager pluginManager;

    /**
     * Creates a new {@code FrameworkBootstrap} with a default {@link PluginManager}.
     */
    public FrameworkBootstrap() {
        this.pluginManager = new PluginManager();
    }

    /**
     * Initialises the framework: discovers plugins and prepares runtime services.
     */
    public void initialize() {
        logger.info("Initialising YAPPC Framework...");
        pluginManager.discoverPlugins();
        logger.info("YAPPC Framework initialised");
    }

    /**
     * Gracefully shuts down the framework and all managed plugins.
     */
    public void shutdown() {
        logger.info("Shutting down YAPPC Framework...");
        pluginManager.shutdown();
        logger.info("YAPPC Framework shut down");
    }

    /**
     * Returns the framework {@link PluginManager}.
     *
     * @return the plugin manager
     */
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    /**
     * Convenience method returning all discovered {@link BuildGeneratorPlugin} instances.
     *
     * @return list of build-generator plugins
     */
    public List<BuildGeneratorPlugin> getBuildGenerators() {
        return pluginManager.getBuildGenerators();
    }
}
