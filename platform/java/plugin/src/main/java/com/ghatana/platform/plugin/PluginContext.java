package com.ghatana.platform.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Context provided to a plugin during initialization.
 * <p>
 * Provides access to configuration, other plugins, and platform services.
 *
 * @doc.type interface
 * @doc.purpose Plugin sandbox and service locator
 * @doc.layer core
 */
public interface PluginContext {

    /**
     * Retrieves typed configuration for the plugin.
     *
     * @param configType the configuration class
     * @param <T> configuration type
     * @return configuration instance, or null if not available
     */
    @Nullable
    <T> T getConfig(@NotNull Class<T> configType);

    /**
     * Retrieves a configuration value by key with a default fallback.
     *
     * @param key the configuration key
     * @param defaultValue value to return if key is not found
     * @return the configuration value or defaultValue
     */
    default @NotNull String getConfig(@NotNull String key, @NotNull String defaultValue) {
        return defaultValue;
    }

    /**
     * Returns the full configuration map for this plugin.
     *
     * @return unmodifiable configuration map
     */
    default @NotNull Map<String, Object> getConfigMap() {
        return Map.of();
    }

    /**
     * Returns the runtime environment (e.g., "production", "staging", "test").
     *
     * @return the environment name
     */
    default @NotNull String getEnvironment() {
        return "production";
    }

    /**
     * Returns the tenant identifier, if applicable.
     *
     * @return tenant ID, or null if not in a tenant context
     */
    default @Nullable String getTenantId() {
        return null;
    }

    /**
     * Finds another plugin by ID.
     */
    @NotNull
    <T extends Plugin> Optional<T> findPlugin(@NotNull String pluginId);

    /**
     * Finds all plugins with a specific capability.
     */
    @NotNull
    List<Plugin> findPluginsByCapability(@NotNull Class<? extends PluginCapability> capability);

    /**
     * Returns the interaction bus for inter-plugin communication.
     */
    @NotNull
    PluginInteractionBus getInteractionBus();
}
