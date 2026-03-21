package com.ghatana.kernel.config;

import com.ghatana.kernel.context.KernelTenantContext;
import io.activej.promise.Promise;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Hierarchical config resolver: cross-product → product-specific → kernel default.
 *
 * <p>Resolves configuration values using the following hierarchy (highest to lowest priority):
 * <ol>
 *   <li>Cross-product config (shared across products)</li>
 *   <li>Product-specific config</li>
 *   <li>Kernel default config</li>
 * </ol></p>
 *
 * @deprecated Use {@link HierarchicalKernelConfigResolver} instead. This class uses
 *             product id strings for config resolution. The canonical replacement uses
 *             scope-aware hierarchical resolution with tenant/product/kernel priority.
 *             Per KERNEL_CANONICALIZATION_DECISIONS.md Day 10 cleanup.
 *
 * @doc.type class
 * @doc.purpose Cross-product config resolution with hierarchical fallback chain
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@Deprecated(forRemoval = true)
public class CrossProductConfigResolver implements KernelConfigResolver {

    private final Map<String, KernelConfigResolver> productResolvers;
    private final DataCloudPlatform dataCloud;
    private final Map<String, Object> kernelDefaults;

    public CrossProductConfigResolver(DataCloudPlatform dataCloud) {
        this.dataCloud = dataCloud;
        this.productResolvers = new HashMap<>();
        this.kernelDefaults = new HashMap<>();
        initializeDefaults();
    }

    private void initializeDefaults() {
        // Kernel default configurations
        kernelDefaults.put("connection.timeout", 30000);
        kernelDefaults.put("retry.attempts", 3);
        kernelDefaults.put("cache.ttl", 300);
        kernelDefaults.put("logging.level", "INFO");
        kernelDefaults.put("health.check.interval", 60);
    }

    @Override
    public <T> T resolve(String configKey, Class<T> type, KernelTenantContext context) {
        // Try cross-product config first
        Optional<T> crossProductValue = resolveFromDataCloud("cross-product." + configKey, type, context);
        if (crossProductValue.isPresent()) {
            return crossProductValue.get();
        }

        // Try product-specific config
        String productId = context.getCurrentProduct();
        if (productId != null && productResolvers.containsKey(productId)) {
            String productKey = productId + "." + configKey;
            T productValue = productResolvers.get(productId)
                .resolve(productKey, type, context);
            if (productValue != null) {
                return productValue;
            }
        }

        // Fall back to kernel default
        return resolveKernelDefault(configKey, type);
    }

    @Override
    public <T> T resolveWithDefault(String configKey, Class<T> type, T defaultValue, KernelTenantContext context) {
        T value = resolve(configKey, type, context);
        return value != null ? value : defaultValue;
    }

    @Override
    public void addConfigProvider(ConfigProvider provider) {
        // Store global provider (product-specific routing handled by resolve logic)
    }

    @Override
    public Promise<Void> reloadConfig(String tenantId) {
        // Reload config from Data-Cloud
        return Promise.ofFuture(dataCloud.reloadConfig(tenantId))
            .whenException(e -> System.err.println("Config reload failed: " + e.getMessage()));
    }

    /**
     * Resolves config from Data-Cloud.
     */
    private <T> Optional<T> resolveFromDataCloud(String configKey, Class<T> type, KernelTenantContext context) {
        try {
            Object value = dataCloud.getConfig(configKey, context.getTenantId());
            if (value == null) {
                return Optional.empty();
            }
            if (type.isInstance(value)) {
                return Optional.of(type.cast(value));
            }
            // Try conversion for common types
            if (type == Integer.class && value instanceof Number) {
                return Optional.of(type.cast(((Number) value).intValue()));
            }
            if (type == Long.class && value instanceof Number) {
                return Optional.of(type.cast(((Number) value).longValue()));
            }
            if (type == Double.class && value instanceof Number) {
                return Optional.of(type.cast(((Number) value).doubleValue()));
            }
            if (type == String.class) {
                return Optional.of(type.cast(value.toString()));
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Resolves from kernel defaults.
     */
    @SuppressWarnings("unchecked")
    private <T> T resolveKernelDefault(String configKey, Class<T> type) {
        Object value = kernelDefaults.get(configKey);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }

    /**
     * Registers a product-specific config resolver.
     *
     * @param productId the product ID
     * @param resolver the resolver
     */
    public void registerProductResolver(String productId, KernelConfigResolver resolver) {
        productResolvers.put(productId, resolver);
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

    // ==================== Inner Types ====================

    @Override
    public <T> Optional<T> resolveOptional(String configKey, Class<T> type, KernelTenantContext context) {
        T value = resolve(configKey, type, context);
        return Optional.ofNullable(value);
    }

    @Override
    public java.util.List<String> getAvailableKeys(KernelTenantContext context) {
        return new java.util.ArrayList<>(kernelDefaults.keySet());
    }

        /**
     * Data-Cloud platform interface for config access.
     */
    public interface DataCloudPlatform {
        Object getConfig(String key, String tenantId);
        java.util.concurrent.CompletableFuture<Void> reloadConfig(String tenantId);
    }

    /**
     * Product-specific config resolver.
     */
    private static class ProductConfigResolver implements KernelConfigResolver {
        private final String productId;
        private final DataCloudPlatform dataCloud;
        private final Map<String, Object> defaults;

        ProductConfigResolver(String productId, DataCloudPlatform dataCloud) {
            this.productId = productId;
            this.dataCloud = dataCloud;
            this.defaults = new HashMap<>();
        }

        @Override
        public <T> T resolve(String configKey, Class<T> type, KernelTenantContext context) {
            try {
                Object value = dataCloud.getConfig(configKey, context.getTenantId());
                if (value != null && type.isInstance(value)) {
                    return type.cast(value);
                }
            } catch (Exception e) {
                // Fall through to defaults
            }
            return resolveDefault(configKey, type);
        }

        @Override
        public <T> T resolveWithDefault(String configKey, Class<T> type, T defaultValue, KernelTenantContext context) {
            T value = resolve(configKey, type, context);
            return value != null ? value : defaultValue;
        }

        @Override
        public void addConfigProvider(ConfigProvider provider) {
            // Not used in product resolver
        }

        @Override
        public Promise<Void> reloadConfig(String tenantId) {
            return Promise.complete();
        }

        @Override
        public <T> Optional<T> resolveOptional(String configKey, Class<T> type, KernelTenantContext context) {
            return Optional.ofNullable(resolve(configKey, type, context));
        }

        @Override
        public java.util.List<String> getAvailableKeys(KernelTenantContext context) {
            return java.util.Collections.emptyList();
        }

                @SuppressWarnings("unchecked")
        private <T> T resolveDefault(String configKey, Class<T> type) {
            Object value = defaults.get(configKey);
            if (value != null && type.isInstance(value)) {
                return type.cast(value);
            }
            return null;
        }
    }
}
