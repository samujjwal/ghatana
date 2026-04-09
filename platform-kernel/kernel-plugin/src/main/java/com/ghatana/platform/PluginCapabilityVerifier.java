/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Verifies plugin capabilities against approval requirements.
 *
 * @doc.type class
 * @doc.purpose Plugin capability verification and approval
 * @doc.layer platform
 * @doc.pattern Verifier
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public final class PluginCapabilityVerifier {

    private static final Logger log = LoggerFactory.getLogger(PluginCapabilityVerifier.class);

    // Pre-approved capabilities for each tier
    private static final Set<String> T1_APPROVED_CAPABILITIES = Set.of(
        "config.read", "config.write"
    );

    private static final Set<String> T2_APPROVED_CAPABILITIES = Set.of(
        "config.read", "config.write", "script.execute", "memory.allocate"
    );

    private static final Set<String> T3_APPROVED_CAPABILITIES = Set.of(
        "config.read", "config.write", "script.execute", "memory.allocate",
        "network.access", "file.system", "process.spawn"
    );

    /**
     * Verifies plugin capabilities against tier requirements.
     *
     * @param capabilities the plugin capabilities
     * @throws PluginCapabilityException if verification fails
     */
    public void verifyCapabilities(Set<String> capabilities) throws PluginCapabilityException {
        log.debug("Verifying plugin capabilities: {}", capabilities);

        if (capabilities == null || capabilities.isEmpty()) {
            throw new PluginCapabilityException("Plugin must declare at least one capability");
        }

        // Verify each capability is valid
        for (String capability : capabilities) {
            if (!isValidCapability(capability)) {
                throw new PluginCapabilityException("Invalid capability: " + capability);
            }
        }

        log.debug("Plugin capabilities verified: {}", capabilities);
    }

    /**
     * Validates runtime capabilities for a loaded plugin.
     *
     * @param plugin the enhanced plugin
     * @return validation result
     */
    public EnhancedPluginManager.CapabilityValidationResult validateRuntimeCapabilities(
            EnhancedPluginManager.EnhancedLoadedPlugin plugin) {

        try {
            Set<String> approvedCapabilities = getApprovedCapabilities(plugin.tier());

            for (String capability : plugin.capabilities()) {
                if (!approvedCapabilities.contains(capability)) {
                    return EnhancedPluginManager.CapabilityValidationResult.failure(
                        String.format("Capability '%s' not approved for tier %d",
                            capability, plugin.tier().getLevel()));
                }
            }

            return EnhancedPluginManager.CapabilityValidationResult.success();

        } catch (Exception e) {
            log.error("Failed to validate runtime capabilities for plugin: {}",
                plugin.basicPlugin().id(), e);
            return EnhancedPluginManager.CapabilityValidationResult.failure(e.getMessage());
        }
    }

    /**
     * Gets approved capabilities for a tier.
     *
     * @param tier the plugin tier
     * @return set of approved capabilities
     */
    private Set<String> getApprovedCapabilities(PluginTier tier) {
        return switch (tier) {
            case T1 -> T1_APPROVED_CAPABILITIES;
            case T2 -> T2_APPROVED_CAPABILITIES;
            case T3 -> T3_APPROVED_CAPABILITIES;
        };
    }

    /**
     * Checks if a capability string is valid.
     *
     * @param capability the capability to check
     * @return true if valid
     */
    private boolean isValidCapability(String capability) {
        if (capability == null || capability.trim().isEmpty()) {
            return false;
        }

        // Capability should be in format: domain.action
        String[] parts = capability.split("\\.");
        return parts.length == 2 &&
               parts[0].matches("^[a-z]+$") &&
               parts[1].matches("^[a-z]+$");
    }
}
