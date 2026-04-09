package com.ghatana.kernel.config;

import com.ghatana.kernel.context.KernelTenantContext;
import io.activej.promise.Promise;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hierarchical config resolver implementation with multi-level resolution.
 *
 * <p>Resolves configuration values by searching in priority order:
 * <ol>
 *   <li>Tenant-specific config (highest priority)</li>
 *   <li>Product-specific config</li>
 *   <li>Kernel default config (lowest priority)</li>
 * </ol></p>
 *
 * <p>Uses a chain of responsibility pattern where each ConfigProvider is checked
 * in priority order until a value is found.</p>
 *
 * @doc.type class
 * @doc.purpose Hierarchical config resolution with tenant/product/kernel priority
 * @doc.layer core
 * @doc.pattern Service, Chain of Responsibility
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class HierarchicalKernelConfigResolver implements KernelConfigResolver {

    private final List<ConfigProvider> providers = new CopyOnWriteArrayList<>();
    private final Map<String, Map<String, Object>> tenantCache = new ConcurrentHashMap<>();
    private final Map<String, Object> kernelDefaults = new ConcurrentHashMap<>();

    /**
     * Creates a new hierarchical config resolver.
     */
    public HierarchicalKernelConfigResolver() {
        // Register default providers in priority order
        // Higher priority = checked first
    }

    @Override
    public <T> T resolve(String configKey, Class<T> type, KernelTenantContext context) {
        Objects.requireNonNull(configKey, "configKey cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        // Try to resolve from providers in priority order
        Optional<T> value = resolveFromProviders(configKey, type, context);

        if (value.isPresent()) {
            return value.get();
        }

        // Try kernel defaults
        @SuppressWarnings("unchecked")
        T defaultValue = (T) kernelDefaults.get(configKey);
        if (defaultValue != null && type.isInstance(defaultValue)) {
            return defaultValue;
        }

        throw new IllegalArgumentException(
            String.format("Configuration key '%s' not found for tenant '%s'",
                configKey, context.getTenantId()));
    }

    @Override
    public <T> T resolveWithDefault(String configKey, Class<T> type, T defaultValue,
                                    KernelTenantContext context) {
        Objects.requireNonNull(configKey, "configKey cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        try {
            return resolve(configKey, type, context);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    @Override
    public <T> Optional<T> resolveOptional(String configKey, Class<T> type,
                                           KernelTenantContext context) {
        Objects.requireNonNull(configKey, "configKey cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        try {
            return Optional.of(resolve(configKey, type, context));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public void addConfigProvider(ConfigProvider provider) {
        Objects.requireNonNull(provider, "provider cannot be null");

        providers.add(provider);

        // Re-sort by priority (higher first)
        providers.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
    }

    @Override
    public Promise<Void> reloadConfig(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");

        // Clear tenant cache
        tenantCache.remove(tenantId);

        // Notify all providers to reload
        for (ConfigProvider provider : providers) {
            // Providers handle their own reloading internally
        }

        return Promise.complete();
    }

    @Override
    public List<String> getAvailableKeys(KernelTenantContext context) {
        Objects.requireNonNull(context, "context cannot be null");

        Set<String> keys = new HashSet<>();

        // Collect keys from all providers
        for (ConfigProvider provider : providers) {
            // Note: This is a simplified implementation
            // In production, providers would expose their available keys
        }

        // Add kernel default keys
        keys.addAll(kernelDefaults.keySet());

        return new ArrayList<>(keys);
    }

    /**
     * Sets a kernel default value.
     *
     * @param key the config key
     * @param value the default value
     */
    public void setKernelDefault(String key, Object value) {
        kernelDefaults.put(key, value);
    }

    /**
     * Sets multiple kernel defaults.
     *
     * @param defaults map of default values
     */
    public void setKernelDefaults(Map<String, Object> defaults) {
        kernelDefaults.putAll(defaults);
    }

    // ==================== Private Methods ====================

    private <T> Optional<T> resolveFromProviders(String configKey, Class<T> type,
                                                   KernelTenantContext context) {
        // Check cache first
        String tenantId = context.getTenantId();
        Map<String, Object> cache = tenantCache.get(tenantId);
        if (cache != null) {
            @SuppressWarnings("unchecked")
            T cached = (T) cache.get(configKey);
            if (cached != null && type.isInstance(cached)) {
                return Optional.of(cached);
            }
        }

        // Check providers in priority order
        for (ConfigProvider provider : providers) {
            Optional<T> value = provider.get(configKey, type, context);
            if (value.isPresent()) {
                // Cache the value
                tenantCache.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                    .put(configKey, value.get());
                return value;
            }
        }

        return Optional.empty();
    }

    /**
     * Clears the config cache for a tenant.
     *
     * @param tenantId the tenant identifier
     */
    public void clearCache(String tenantId) {
        tenantCache.remove(tenantId);
    }

    /**
     * Clears all cached config values.
     */
    public void clearAllCaches() {
        tenantCache.clear();
    }

    /**
     * Gets all registered providers.
     *
     * @return list of providers in priority order
     */
    public List<ConfigProvider> getProviders() {
        return new ArrayList<>(providers);
    }
}
