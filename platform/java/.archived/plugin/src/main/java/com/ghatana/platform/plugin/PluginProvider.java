package com.ghatana.platform.plugin;

import org.jetbrains.annotations.NotNull;

/**
 * Service Provider Interface for plugin discovery.
 * <p>
 * Implementations of this interface are discovered via {@link java.util.ServiceLoader}
 * and provide factory methods for creating plugin instances. This enables dynamic
 * plugin discovery without requiring compile-time knowledge of plugin implementations.
 * <p>
 * To register a plugin provider, create a file at:
 * {@code META-INF/services/com.ghatana.platform.plugin.PluginProvider}
 * containing the fully-qualified class name of the implementation.
 * <p>
 * Example:
 * <pre>
 * public class MyPluginProvider implements PluginProvider {
 *     public Plugin createPlugin() { return new MyPlugin(); }
 *     public PluginMetadata getMetadata() { return myMetadata; }
 *     public int priority() { return 100; }
 * }
 * </pre>
 *
 * @doc.type interface
 * @doc.purpose ServiceLoader-based plugin discovery
 * @doc.layer core
 */
public interface PluginProvider {

    /**
     * Creates a new instance of the plugin.
     * <p>
     * Each call should return a fresh, un-initialized plugin instance.
     * The plugin will be initialized later via {@link Plugin#initialize(PluginContext)}.
     *
     * @return a new plugin instance
     */
    @NotNull
    Plugin createPlugin();

    /**
     * Returns metadata describing the plugin that this provider creates.
     * <p>
     * This allows the registry to inspect plugin metadata before creating
     * plugin instances (e.g., for dependency resolution ordering).
     *
     * @return the plugin metadata
     */
    @NotNull
    PluginMetadata getMetadata();

    /**
     * Returns the priority of this provider for ordering purposes.
     * <p>
     * Lower values indicate higher priority. Providers with higher priority
     * are initialized first. Default priority is 1000.
     *
     * @return the priority value (lower = higher priority)
     */
    default int priority() {
        return 1000;
    }

    /**
     * Returns whether this provider is enabled.
     * <p>
     * Disabled providers are skipped during discovery. This can be used
     * for feature-flag based plugin activation.
     *
     * @return true if this provider should be used
     */
    default boolean isEnabled() {
        return true;
    }
}
