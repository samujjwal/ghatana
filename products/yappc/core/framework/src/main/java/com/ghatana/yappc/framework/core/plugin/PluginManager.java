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

package com.ghatana.yappc.framework.core.plugin;

import com.ghatana.yappc.framework.api.domain.BuildSystemType;
import com.ghatana.yappc.framework.api.plugin.BuildGeneratorPlugin;
import com.ghatana.yappc.framework.api.plugin.YappcPlugin;
import com.ghatana.yappc.framework.core.plugin.sandbox.IsolatingPluginSandbox;
import com.ghatana.yappc.framework.core.plugin.sandbox.PluginDescriptor;
import com.ghatana.yappc.framework.core.plugin.sandbox.PluginLoadException;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Framework-level plugin manager that discovers and manages {@link BuildGeneratorPlugin}
 * instances using the Java {@link ServiceLoader} mechanism.
 *
 * <p>This is the framework-specific plugin manager (distinct from the scaffold-core
 * {@code com.ghatana.yappc.core.plugin.PluginManager}) and is used by
 * {@link com.ghatana.yappc.framework.core.FrameworkBootstrap} to drive the integration-test
 * plugin workflow.</p>
 *
 * @doc.type class
 * @doc.purpose Framework plugin discovery and management
 * @doc.layer product
 * @doc.pattern Manager
 */
public class PluginManager {

    private static final Logger logger = LoggerFactory.getLogger(PluginManager.class);

    private final List<YappcPlugin> plugins = new CopyOnWriteArrayList<>();

    /**
     * Sandbox used for ClassLoader-isolated, permission-enforced plugin loading.
     * Defaults to the current platform version marker; override via
     * {@link #PluginManager(String)}.
     */
    private final IsolatingPluginSandbox sandbox;

    /** Creates a manager using the running platform version for sandbox checks. */
    public PluginManager() {
        this.sandbox = new IsolatingPluginSandbox(PlatformVersion.CURRENT);
    }

    /**
     * Creates a manager with a specific platform version for sandbox compatibility checks.
     * Useful for testing or version-override scenarios.
     *
     * @param platformVersion current platform version string (SemVer)
     */
    public PluginManager(String platformVersion) {
        this.sandbox = new IsolatingPluginSandbox(platformVersion);
    }

    /**
     * Loads a plugin securely using the {@link IsolatingPluginSandbox}.
     *
     * <p>The plugin is loaded in an isolated {@link java.net.URLClassLoader} and
     * wrapped with a {@link com.ghatana.yappc.framework.core.plugin.sandbox.PermissionProxy}
     * that enforces the descriptor's {@link com.ghatana.yappc.framework.core.plugin.sandbox.PermissionSet}.
     *
     * @param <T>        plugin contract type
     * @param descriptor plugin descriptor carrying classpath and permissions
     * @param contract   interface the returned plugin must implement
     * @return permission-enforcing proxy wrapping the loaded plugin
     * @throws PluginLoadException if loading fails (version incompatibility, class not found, etc.)
     */
    public <T> T loadSecure(PluginDescriptor descriptor, Class<T> contract) throws PluginLoadException {
        logger.info("loadSecure: {} (contract={})", descriptor.logId(), contract.getSimpleName());
        T plugin = sandbox.loadPlugin(descriptor, contract);
        if (plugin instanceof YappcPlugin yp) {
            plugins.add(yp);
        }
        return plugin;
    }

    /**
     * Discovers plugins on the classpath via {@link ServiceLoader}.
     */
    public void discoverPlugins() {
        ServiceLoader<BuildGeneratorPlugin> loader = ServiceLoader.load(BuildGeneratorPlugin.class);
        for (BuildGeneratorPlugin plugin : loader) {
            plugins.add(plugin);
            logger.info("Discovered build-generator plugin: {} v{}", plugin.getName(), plugin.getVersion());
        }
        logger.info("Plugin discovery complete – {} plugin(s) found", plugins.size());
    }

    /**
     * Returns all discovered {@link BuildGeneratorPlugin} instances.
     *
     * @return an unmodifiable list of build-generator plugins
     */
    public List<BuildGeneratorPlugin> getBuildGenerators() {
        List<BuildGeneratorPlugin> result = new ArrayList<>();
        for (YappcPlugin plugin : plugins) {
            if (plugin instanceof BuildGeneratorPlugin bgp) {
                result.add(bgp);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Returns build-generator plugins that are relevant for the given build-system type.
     *
     * @param buildSystemType the build system to filter for
     * @return filtered list of build-generator plugins
     */
    public List<BuildGeneratorPlugin> getBuildGenerators(BuildSystemType buildSystemType) {
        List<BuildGeneratorPlugin> result = new ArrayList<>();
        for (YappcPlugin plugin : plugins) {
            if (plugin instanceof BuildGeneratorPlugin bgp && bgp.isEnabled()) {
                result.add(bgp);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Returns aggregate statistics about discovered plugins.
     *
     * @return plugin statistics snapshot
     */
    public PluginStatistics getStatistics() {
        int total = plugins.size();
        int enabled = 0;
        for (YappcPlugin plugin : plugins) {
            if (plugin instanceof BuildGeneratorPlugin bgp && bgp.isEnabled()) {
                enabled++;
            }
        }
        return new PluginStatistics(total, enabled);
    }

    /**
     * Shuts down all managed plugins and clears the registry.
     */
    public void shutdown() {
        plugins.clear();
        logger.info("PluginManager shut down – all plugins cleared");
    }

    /**
     * Snapshot of plugin statistics.
     *
     * @param totalPlugins  total number of discovered plugins
     * @param enabledPlugins number of enabled plugins
     */
    public record PluginStatistics(int totalPlugins, int enabledPlugins) {
        @Override
        public String toString() {
            return "PluginStatistics{total=" + totalPlugins + ", enabled=" + enabledPlugins + "}";
        }
    }
}
