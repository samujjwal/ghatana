package com.ghatana.kernel.plugin;

import com.ghatana.kernel.registry.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Discovers kernel plugins via ServiceLoader and registers them in the plugin registry.
 *
 * @doc.type class
 * @doc.purpose ServiceLoader-based plugin discovery and registration
 * @doc.layer core
 * @doc.pattern Service, Discovery
 */
public final class PluginDiscoveryService {

    private static final Logger LOG = LoggerFactory.getLogger(PluginDiscoveryService.class);

    private final PluginRegistry pluginRegistry;
    private final ClassLoader classLoader;

    public PluginDiscoveryService(PluginRegistry pluginRegistry) {
        this(pluginRegistry, Thread.currentThread().getContextClassLoader());
    }

    public PluginDiscoveryService(PluginRegistry pluginRegistry, ClassLoader classLoader) {
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry, "pluginRegistry cannot be null");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader cannot be null");
    }

    /**
     * Discovers plugins and registers each one into the registry.
     *
     * @return number of plugins successfully registered
     */
    public int discoverAndRegister() {
        int discoveredCount = 0;
        ServiceLoader<KernelPlugin> loader = ServiceLoader.load(KernelPlugin.class, classLoader);

        for (KernelPlugin plugin : loader) {
            try {
                pluginRegistry.registerPlugin(plugin);
                discoveredCount++;
                LOG.info("Discovered and registered plugin: {} v{}", plugin.getModuleId(), plugin.getVersion());
            } catch (Exception exception) {
                LOG.warn("Failed to register discovered plugin: {}", plugin.getClass().getName(), exception);
            }
        }

        LOG.info("Plugin discovery completed. Registered {} plugin(s)", discoveredCount);
        return discoveredCount;
    }
}
