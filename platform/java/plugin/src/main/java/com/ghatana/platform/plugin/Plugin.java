package com.ghatana.platform.plugin;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;

/**
 * Standard interface for all Ghatana plugins.
 * <p>
 * Plugins can extend the platform with new Organizations, Tools, Operators, or Agents.
 *
 * @doc.type interface
 * @doc.purpose Canonical Plugin definition
 * @doc.layer core
 */
public interface Plugin {

    /**
     * Returns the metadata of this plugin.
     */
    @NotNull
    PluginMetadata metadata();

    /**
     * Returns the current state of the plugin.
     */
    @NotNull
    PluginState getState();

    /**
     * Initializes the plugin with the given context.
     *
     * @param context The plugin context
     * @return A Promise resolving when initialization is complete
     */
    @NotNull
    Promise<Void> initialize(@NotNull PluginContext context);

    /**
     * Starts the plugin.
     */
    @NotNull
    Promise<Void> start();

    /**
     * Stops the plugin.
     */
    @NotNull
    Promise<Void> stop();

    /**
     * Shuts down the plugin and releases all resources.
     * <p>
     * This is called after {@link #stop()} to perform final cleanup such as
     * closing connections, releasing file handles, and deregistering listeners.
     * The default implementation delegates to {@link #stop()}.
     *
     * @return A Promise resolving when shutdown is complete
     */
    @NotNull
    default Promise<Void> shutdown() {
        return stop();
    }

    /**
     * Performs a health check.
     */
    @NotNull
    default Promise<HealthStatus> healthCheck() {
        return Promise.of(HealthStatus.ok());
    }

    /**
     * Returns the capabilities provided by this plugin.
     */
    @NotNull
    default Set<PluginCapability> getCapabilities() {
        return Set.of();
    }

    /**
     * Retrieves a specific capability implementation.
     */
    @NotNull
    default <T extends PluginCapability> Optional<T> getCapability(@NotNull Class<T> capabilityType) {
        return getCapabilities().stream()
            .filter(capabilityType::isInstance)
            .map(capabilityType::cast)
            .findFirst();
    }
}
