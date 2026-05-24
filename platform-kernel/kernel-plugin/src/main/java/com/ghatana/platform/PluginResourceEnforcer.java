/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces resource quotas for plugins.
 *
 * @doc.type class
 * @doc.purpose Plugin resource quota enforcement
 * @doc.layer platform
 * @doc.pattern Enforcer
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public final class PluginResourceEnforcer {

    private static final Logger log = LoggerFactory.getLogger(PluginResourceEnforcer.class);

    /**
     * Validates that resource quotas are within limits.
     *
     * @param quotas the resource quotas
     * @throws PluginResourceException if quotas exceed limits
     */
    public void validateQuotas(com.ghatana.kernel.plugin.PluginResourceQuota quotas) throws PluginResourceException {
        log.debug("Validating resource quotas: {}", quotas);

        if (quotas == null) {
            throw new PluginResourceException("Resource quotas cannot be null");
        }

        // Validate memory quota
        if (quotas.getMaxMemoryMb() <= 0 || quotas.getMaxMemoryMb() > getMaxMemoryForTier(quotas.getTier())) {
            throw new PluginResourceException(
                String.format("Invalid memory quota: %d MB (max: %d MB)",
                    quotas.getMaxMemoryMb(), getMaxMemoryForTier(quotas.getTier())));
        }

        // Validate CPU quota
        if (quotas.getMaxCpuPercent() <= 0 || quotas.getMaxCpuPercent() > getMaxCpuForTier(quotas.getTier())) {
            throw new PluginResourceException(
                String.format("Invalid CPU quota: %d%% (max: %d%%)",
                    quotas.getMaxCpuPercent(), getMaxCpuForTier(quotas.getTier())));
        }

        // Validate file descriptor quota
        if (quotas.getMaxFileDescriptors() <= 0
                || quotas.getMaxFileDescriptors() > getMaxFileDescriptorsForTier(quotas.getTier())) {
            throw new PluginResourceException(
                String.format("Invalid file descriptor quota: %d (max: %d)",
                    quotas.getMaxFileDescriptors(), getMaxFileDescriptorsForTier(quotas.getTier())));
        }

        log.debug("Resource quotas validated: {}", quotas);
    }

    /**
     * Enforces resource quotas for a running plugin.
     *
     * @param plugin the enhanced plugin
     * @return enforcement result
     */
    public EnhancedPluginManager.ResourceEnforcementResult enforceQuotas(
            EnhancedPluginManager.EnhancedLoadedPlugin plugin) {

        try {
            com.ghatana.kernel.plugin.PluginResourceQuota quotas = plugin.resourceQuota();

            // Check current resource usage (baseline implementation)
            ResourceUsage currentUsage = getCurrentResourceUsage(plugin.basicPlugin().id());

            // Enforce memory quota
            if (currentUsage.memoryMB() > quotas.getMaxMemoryMb()) {
                return EnhancedPluginManager.ResourceEnforcementResult.failure(
                    String.format("Memory usage exceeded: %d MB > %d MB",
                        currentUsage.memoryMB(), quotas.getMaxMemoryMb()));
            }

            // Enforce CPU quota
            if (currentUsage.cpuPercent() > quotas.getMaxCpuPercent()) {
                return EnhancedPluginManager.ResourceEnforcementResult.failure(
                    String.format("CPU usage exceeded: %d%% > %d%%",
                        currentUsage.cpuPercent(), quotas.getMaxCpuPercent()));
            }

            // Enforce file descriptor quota
            if (currentUsage.fileDescriptors() > quotas.getMaxFileDescriptors()) {
                return EnhancedPluginManager.ResourceEnforcementResult.failure(
                    String.format("File descriptor usage exceeded: %d > %d",
                        currentUsage.fileDescriptors(), quotas.getMaxFileDescriptors()));
            }

            return EnhancedPluginManager.ResourceEnforcementResult.success();

        } catch (Exception e) {
            log.error("Failed to enforce resource quotas for plugin: {}",
                plugin.basicPlugin().id(), e);
            return EnhancedPluginManager.ResourceEnforcementResult.failure(e.getMessage());
        }
    }

    /**
     * Gets maximum memory allowed for a tier.
     */
    private int getMaxMemoryForTier(com.ghatana.kernel.plugin.PluginTier tier) {
        return switch (tier) {
            case T1 -> 64;   // 64 MB for config-only plugins
            case T2 -> 512;  // 512 MB for scripted plugins
            case T3 -> 2048; // 2 GB for network-capable plugins
        };
    }

    /**
     * Gets maximum CPU percentage allowed for a tier.
     */
    private int getMaxCpuForTier(com.ghatana.kernel.plugin.PluginTier tier) {
        return switch (tier) {
            case T1 -> 5;   // 5% for config-only plugins
            case T2 -> 25;  // 25% for scripted plugins
            case T3 -> 75;  // 75% for network-capable plugins
        };
    }

    /**
     * Gets maximum file descriptors allowed for a tier.
     */
    private int getMaxFileDescriptorsForTier(com.ghatana.kernel.plugin.PluginTier tier) {
        return switch (tier) {
            case T1 -> 10;   // 10 for config-only plugins
            case T2 -> 100;  // 100 for scripted plugins
            case T3 -> 1000; // 1000 for network-capable plugins
        };
    }

    /**
     * Gets current resource usage for a plugin (baseline implementation).
     */
    private ResourceUsage getCurrentResourceUsage(String pluginId) {
        // This would integrate with system monitoring to get actual usage
        return new ResourceUsage(32, 10, 5); // 32 MB, 10% CPU, 5 file descriptors
    }

    /**
     * Current resource usage metrics.
     */
    private record ResourceUsage(int memoryMB, int cpuPercent, int fileDescriptors) {}
}
