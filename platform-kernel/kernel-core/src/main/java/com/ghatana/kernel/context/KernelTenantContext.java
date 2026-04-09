package com.ghatana.kernel.context;

import io.activej.promise.Promise;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Per-request tenant context carrying tenant identity, feature flags, config, and security.
 *
 * <p>The tenant context provides isolation between different tenants in the system.
 * It carries tenant-specific configuration, feature flags, and security context.</p>
 *
 * @doc.type class
 * @doc.purpose Tenant-scoped runtime context for feature gating, config, and security
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class KernelTenantContext {

    private final String tenantId;
    private final TenantType tenantType;
    private final Map<String, Object> tenantConfig;
    private final Set<String> enabledFeatures;
    private final SecurityContext securityContext;
    private final Executor executor;

    /**
     * Creates a tenant context.
     *
     * @param tenantId the unique tenant identifier
     * @param tenantType the type of tenant
     * @param tenantConfig tenant-specific configuration
     * @param enabledFeatures set of enabled feature flags
     * @param securityContext the security context
     * @param executor executor for async operations
     */
    public KernelTenantContext(String tenantId, TenantType tenantType,
                               Map<String, Object> tenantConfig,
                               Set<String> enabledFeatures,
                               SecurityContext securityContext,
                               Executor executor) {
        this.tenantId = tenantId;
        this.tenantType = tenantType != null ? tenantType : TenantType.STANDARD;
        this.tenantConfig = tenantConfig != null ? Map.copyOf(tenantConfig) : Map.of();
        this.enabledFeatures = enabledFeatures != null ? Set.copyOf(enabledFeatures) : Set.of();
        this.securityContext = securityContext;
        this.executor = executor;
    }

    // ==================== Identity ====================

    public String getTenantId() { return tenantId; }
    public TenantType getTenantType() { return tenantType; }

    /**
     * Gets the current product context for this tenant.
     *
     * @return the product ID if set, or null
     */
    public String getCurrentProduct() {
        Object product = tenantConfig.get("product");
        return product instanceof String ? (String) product : null;
    }

    // ==================== Feature Gating ====================

    /**
     * Checks if a feature is enabled for this tenant.
     *
     * @param featureId the feature identifier
     * @return true if enabled
     */
    public boolean isFeatureEnabled(String featureId) {
        return enabledFeatures.contains(featureId);
    }

    /**
     * Async feature check.
     *
     * @param featureId the feature identifier
     * @return Promise that resolves to true if enabled
     */
    public Promise<Boolean> isFeatureEnabledAsync(String featureId) {
        if (executor == null) {
            return Promise.of(isFeatureEnabled(featureId));
        }
        return Promise.ofBlocking(executor, () -> isFeatureEnabled(featureId));
    }

    // ==================== Configuration ====================

    /**
     * Gets a configuration value.
     *
     * @param key the config key
     * @param type the expected type
     * @param <T> the value type
     * @return the config value
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key, Class<T> type) {
        Object value = tenantConfig.get(key);
        if (value == null) return null;
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Gets a configuration value with default.
     *
     * @param key the config key
     * @param defaultValue the default value
     * @param <T> the value type
     * @return the config value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key, T defaultValue) {
        Object value = tenantConfig.get(key);
        if (value == null) return defaultValue;
        if (defaultValue != null && defaultValue.getClass().isInstance(value)) {
            return (T) value;
        }
        return defaultValue;
    }

    /**
     * Gets an optional configuration value.
     *
     * @param key the config key
     * @param type the expected type
     * @param <T> the value type
     * @return optional containing the value if present
     */
    public <T> Optional<T> getOptionalConfig(String key, Class<T> type) {
        return Optional.ofNullable(getConfig(key, type));
    }

    /**
     * Async config reload.
     *
     * @return Promise that completes when config is reloaded
     */
    public Promise<Void> reloadConfig() {
        // In a real implementation, this would reload from config service
        return Promise.complete();
    }

    // ==================== Security ====================

    public SecurityContext getSecurityContext() { return securityContext; }

    /**
     * Checks if the current user has a specific permission.
     *
     * @param permission the permission to check
     * @return true if has permission
     */
    public boolean hasPermission(String permission) {
        return securityContext != null && securityContext.hasPermission(permission);
    }

    /**
     * Checks if the current user has a specific role.
     *
     * @param role the role to check
     * @return true if has role
     */
    public boolean hasRole(String role) {
        return securityContext != null && securityContext.hasRole(role);
    }

    // ==================== Security Context ====================

    /**
     * Security context interface.
     */
    public interface SecurityContext {
        String getUserId();
        Set<String> getRoles();
        Set<String> getPermissions();
        boolean isAuthenticated();
        boolean hasRole(String role);
        boolean hasPermission(String permission);
    }

    // ==================== Tenant Types ====================

    public enum TenantType {
        STANDARD,
        ENTERPRISE,
        TRIAL,
        SYSTEM,
        DEDICATED
    }
}
