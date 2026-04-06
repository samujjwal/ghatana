package com.ghatana.kernel.config;

import com.ghatana.kernel.context.KernelTenantContext;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Hierarchical config resolution: cross-product → product-specific → kernel default.
 *
 * <p>The config resolver provides a hierarchical configuration system where values
 * can be defined at multiple levels (kernel, product, tenant) and are resolved
 * based on priority.</p>
 *
 * @doc.type interface
 * @doc.purpose Hierarchical config resolution across tenant, product, and kernel scopes
 * @doc.layer core
 * @doc.pattern Service, Chain of Responsibility
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface KernelConfigResolver {

    /**
     * Resolves a configuration value.
     *
     * <p>Resolves the configuration value by searching in the following order:
     * <ol>
     *   <li>Tenant-specific config</li>
     *   <li>Product-specific config</li>
     *   <li>Kernel default config</li>
     * </ol></p>
     *
     * @param configKey the configuration key
     * @param type the expected value type
     * @param context the tenant context for scoped resolution
     * @param <T> the value type
     * @return the resolved configuration value
     * @throws IllegalArgumentException if config not found
     */
    <T> T resolve(String configKey, Class<T> type, KernelTenantContext context);

    /**
     * Resolves a configuration value with default.
     *
     * <p>Returns the default value if the config key is not found at any level.</p>
     *
     * @param configKey the configuration key
     * @param type the expected value type
     * @param defaultValue the default value if not found
     * @param context the tenant context
     * @param <T> the value type
     * @return the resolved value or default
     */
    <T> T resolveWithDefault(String configKey, Class<T> type, T defaultValue, KernelTenantContext context);

    /**
     * Resolves an optional configuration value.
     *
     * @param configKey the configuration key
     * @param type the expected value type
     * @param context the tenant context
     * @param <T> the value type
     * @return optional containing the value if present
     */
    <T> Optional<T> resolveOptional(String configKey, Class<T> type, KernelTenantContext context);

    /**
     * Adds a config provider to the resolution chain.
     *
     * <p>Providers are checked in the order they are added.</p>
     *
     * @param provider the config provider to add
     */
    void addConfigProvider(ConfigProvider provider);

    /**
     * Async config reload for a tenant.
     *
     * <p>Clears cached config values and reloads from providers.</p>
     *
     * @param tenantId the tenant identifier
     * @return Promise that completes when reload is finished
     */
    Promise<Void> reloadConfig(String tenantId);

    /**
     * Gets all configuration keys available for a tenant.
     *
     * @param context the tenant context
     * @return list of available config keys
     */
    List<String> getAvailableKeys(KernelTenantContext context);

    /**
     * Configuration provider interface.
     */
    interface ConfigProvider {
        /**
         * Gets a configuration value.
         *
         * @param key the config key
         * @param type the expected type
         * @param context the tenant context
         * @param <T> the value type
         * @return optional containing the value if present
         */
        <T> Optional<T> get(String key, Class<T> type, KernelTenantContext context);

        /**
         * Returns the provider priority.
         *
         * <p>Higher priority providers are checked first.</p>
         *
         * @return the priority (default: 0)
         */
        default int getPriority() {
            return 0;
        }

        /**
         * Returns the provider name.
         *
         * @return the provider name
         */
        String getName();
    }
}
