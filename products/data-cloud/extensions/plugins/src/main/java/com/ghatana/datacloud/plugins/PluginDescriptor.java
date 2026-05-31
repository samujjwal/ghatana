/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugins;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical plugin descriptor for Data Cloud plugins (P8).
 *
 * <p>Provides comprehensive metadata about a plugin including version,
 * capabilities, lifecycle state, policy requirements, and runtime isolation.
 *
 * @doc.type record
 * @doc.purpose Canonical plugin descriptor for lifecycle-driven plugin management
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PluginDescriptor(
        String pluginId,
        String displayName,
        PluginVersion version,
        PluginLifecycleState lifecycleState,
        PluginConfigSchema configSchema,
        Set<PluginCapability> capabilities,
        PluginPolicyRequirements policyRequirements,
        PluginRuntimeIsolation runtimeIsolation,
        PluginHealth health,
        PluginAuditPolicy auditPolicy,
        Instant installedAt,
        Instant lastUpdatedAt,
        String installedBy,
        Map<String, Object> metadata
) {
    public PluginDescriptor {
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (version == null) {
            throw new IllegalArgumentException("version must not be null");
        }
        if (lifecycleState == null) {
            lifecycleState = PluginLifecycleState.INSTALLED;
        }
        if (configSchema == null) {
            configSchema = PluginConfigSchema.empty();
        }
        if (capabilities == null) {
            capabilities = Set.of();
        }
        if (policyRequirements == null) {
            policyRequirements = PluginPolicyRequirements.defaultRequirements();
        }
        if (runtimeIsolation == null) {
            runtimeIsolation = PluginRuntimeIsolation.defaultIsolation();
        }
        if (health == null) {
            health = PluginHealth.unknown();
        }
        if (auditPolicy == null) {
            auditPolicy = PluginAuditPolicy.defaultPolicy();
        }
        if (installedAt == null) {
            installedAt = Instant.now();
        }
        if (lastUpdatedAt == null) {
            lastUpdatedAt = installedAt;
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }

    /**
     * Returns true if the plugin is in an active lifecycle state.
     */
    public boolean isActive() {
        return lifecycleState == PluginLifecycleState.ENABLED
            || lifecycleState == PluginLifecycleState.RUNNING;
    }

    /**
     * Returns true if the plugin can be enabled.
     */
    public boolean canBeEnabled() {
        return lifecycleState == PluginLifecycleState.INSTALLED
            || lifecycleState == PluginLifecycleState.DISABLED
            || lifecycleState == PluginLifecycleState.STOPPED;
    }

    /**
     * Returns true if the plugin can be disabled.
     */
    public boolean canBeDisabled() {
        return lifecycleState == PluginLifecycleState.ENABLED
            || lifecycleState == PluginLifecycleState.RUNNING;
    }

    /**
     * Returns true if the plugin meets all policy requirements.
     */
    public boolean meetsPolicyRequirements() {
        return policyRequirements != null && policyRequirements.allSatisfied();
    }

    /**
     * Returns true if the plugin is healthy.
     */
    public boolean isHealthy() {
        return health != null && health.status() == PluginHealth.HealthStatus.HEALTHY;
    }
}
