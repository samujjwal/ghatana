/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugin;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for managing plugins.
 *
 * <p>Handles plugin registration, lifecycle, and discovery.
 *
 * @doc.type interface
 * @doc.purpose Plugin registry and lifecycle management
 * @doc.layer product
 * @doc.pattern Registry, Lifecycle Management
 */
public interface PluginRegistry {

    /**
     * Register a plugin.
     *
     * @param plugin plugin metadata
     * @return promise of registered plugin
     */
    Promise<PluginMetadata> register(PluginMetadata plugin);

    /**
     * Unregister a plugin.
     *
     * @param pluginId plugin identifier
     * @return promise completing when unregistered
     */
    Promise<Void> unregister(String pluginId);

    /**
     * Get plugin by ID.
     *
     * @param pluginId plugin identifier
     * @return promise of plugin if found
     */
    Promise<Optional<PluginMetadata>> getPlugin(String pluginId);

    /**
     * List all plugins for tenant.
     *
     * @param tenantId tenant identifier
     * @param status optional status filter
     * @return promise of plugin list
     */
    Promise<List<PluginMetadata>> listPlugins(String tenantId, PluginStatus status);

    /**
     * Activate a plugin.
     *
     * @param pluginId plugin identifier
     * @return promise of activated plugin
     */
    Promise<PluginMetadata> activate(String pluginId);

    /**
     * Deactivate a plugin.
     *
     * @param pluginId plugin identifier
     * @return promise of deactivated plugin
     */
    Promise<PluginMetadata> deactivate(String pluginId);

    /**
     * Get plugin configuration.
     *
     * @param pluginId plugin identifier
     * @return promise of configuration
     */
    Promise<Map<String, Object>> getConfiguration(String pluginId);

    /**
     * Update plugin configuration.
     *
     * @param pluginId plugin identifier
     * @param configuration new configuration
     * @return promise of updated configuration
     */
    Promise<Map<String, Object>> updateConfiguration(String pluginId, Map<String, Object> configuration);

    /**
     * Execute plugin hook.
     *
     * @param pluginId plugin identifier
     * @param hookName hook name
     * @param context execution context
     * @return promise of hook result
     */
    Promise<HookResult> executeHook(String pluginId, String hookName, Map<String, Object> context);

    /**
     * Get plugin health status.
     *
     * @param pluginId plugin identifier
     * @return promise of health status
     */
    Promise<PluginHealth> getHealth(String pluginId);

    /**
     * Pass 8: Canonical plugin lifecycle state.
     */
    enum PluginLifecycleState {
        INSTALLED, REGISTERED, ACTIVATING, ACTIVE, DEACTIVATING, INACTIVE, ERROR, UNINSTALLING, UNINSTALLED
    }

    /**
     * Plugin status (legacy, maintained for compatibility).
     */
    enum PluginStatus {
        REGISTERED, ACTIVE, INACTIVE, ERROR, UNREGISTERED
    }

    /**
     * Plugin type.
     */
    enum PluginType {
        DATA_SOURCE, DATA_SINK, TRANSFORM, ANALYTICS, NOTIFICATION, CUSTOM
    }

    /**
     * Pass 8: Plugin descriptor with comprehensive metadata.
     */
    record PluginDescriptor(
        String pluginId,
        String name,
        String description,
        PluginVersion version,
        String tenantId,
        PluginType type,
        PluginLifecycleState lifecycleState,
        PluginConfigSchema configSchema,
        PluginCapability capabilities,
        PluginPolicyRequirements policyRequirements,
        Map<String, Object> manifest,
        Instant installedAt,
        Instant activatedAt,
        String installedBy
    ) {
        public boolean isActive() {
            return lifecycleState == PluginLifecycleState.ACTIVE;
        }
    }

    /**
     * Pass 8: Plugin version with semantic versioning.
     */
    record PluginVersion(
        String version,
        int major,
        int minor,
        int patch,
        String preRelease,
        String buildMetadata
    ) {
        public PluginVersion {
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("Version string is required");
            }
        }

        public static PluginVersion parse(String versionString) {
            String[] parts = versionString.split("\\.");
            int major = parts.length > 0 ? Integer.parseInt(parts[0].split("-")[0].split("\\+")[0]) : 0;
            int minor = parts.length > 1 ? Integer.parseInt(parts[1].split("-")[0].split("\\+")[0]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2].split("-")[0].split("\\+")[0]) : 0;
            String preRelease = versionString.contains("-") ? versionString.substring(versionString.indexOf("-") + 1).split("\\+")[0] : null;
            String buildMetadata = versionString.contains("+") ? versionString.substring(versionString.indexOf("+") + 1) : null;
            return new PluginVersion(versionString, major, minor, patch, preRelease, buildMetadata);
        }
    }

    /**
     * Pass 8: Plugin configuration schema for validation.
     */
    record PluginConfigSchema(
        String schemaId,
        String schemaVersion,
        Map<String, ConfigField> fields,
        Map<String, Object> validationRules
    ) {
        public record ConfigField(
            String name,
            String type,
            boolean required,
            String description,
            Object defaultValue,
            Map<String, Object> constraints
        ) {}
    }

    /**
     * Pass 8: Plugin capability description.
     */
    record PluginCapability(
        List<String> providedHooks,
        List<String> requiredHooks,
        List<String> dataTypes,
        List<String> permissions,
        boolean requiresSandbox,
        boolean requiresIsolation,
        Map<String, Object> resourceRequirements
    ) {}

    /**
     * Pass 8: Plugin policy requirements.
     */
    record PluginPolicyRequirements(
        boolean requiresDataResidency,
        boolean requiresEncryptionAtRest,
        boolean requiresEncryptionInTransit,
        boolean requiresAuditLogging,
        boolean requiresConsentManagement,
        List<String> allowedDataClasses,
        List<String> prohibitedDataClasses,
        Map<String, String> complianceFrameworks
    ) {}

    /**
     * Plugin metadata.
     */
    record PluginMetadata(
        String id,
        String name,
        String description,
        String version,
        String tenantId,
        PluginType type,
        PluginStatus status,
        List<String> hooks,
        List<String> dependencies,
        Map<String, Object> manifest,
        Instant registeredAt,
        Instant activatedAt,
        String registeredBy
    ) {
        /**
         * Check if plugin is active.
         */
        public boolean isActive() {
            return status == PluginStatus.ACTIVE;
        }

        /**
         * Check if plugin has hook.
         */
        public boolean hasHook(String hookName) {
            return hooks != null && hooks.contains(hookName);
        }
    }

    /**
     * Hook execution result.
     */
    record HookResult(
        String pluginId,
        String hookName,
        boolean success,
        Object result,
        String errorMessage,
        long executionTimeMs
    ) {}

    /**
     * Plugin health status.
     */
    record PluginHealth(
        String pluginId,
        boolean healthy,
        String status,
        long lastCheckTime,
        String message,
        List<String> issues
    ) {}
}
