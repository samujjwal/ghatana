/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces plugin tier restrictions and prevents tier escalation.
 *
 * <p>Plugin tiers:
 * <ul>
 *   <li>T1: Configuration-only plugins (no code execution)</li>
 *   <li>T2: Scripted plugins with sandboxed execution</li>
 *   <li>T3: Network-capable plugins with extended permissions</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Plugin tier enforcement and escalation prevention
 * @doc.layer platform
 * @doc.pattern Enforcer
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public final class PluginTierEnforcer {

    private static final Logger log = LoggerFactory.getLogger(PluginTierEnforcer.class);

    /**
     * Validates that a plugin tier is allowed.
     *
     * @param tier the plugin tier
     * @throws PluginTierViolationException if tier is not allowed
     */
    public void validateTier(PluginTier tier) throws PluginTierViolationException {
        log.debug("Validating plugin tier: {}", tier);

        // Check if tier is supported
        if (tier == null) {
            throw new PluginTierViolationException("Plugin tier cannot be null");
        }

        // Additional tier validation logic can be added here
        // For now, all tiers are allowed but with different restrictions

        log.debug("Plugin tier validated: {}", tier);
    }

    /**
     * Checks if a plugin can access a specific capability based on its tier.
     *
     * @param tier the plugin tier
     * @param capability the capability to access
     * @return true if access is allowed
     */
    public boolean canAccessCapability(PluginTier tier, String capability) {
        return switch (tier) {
            case T1 -> canT1Access(capability);
            case T2 -> canT2Access(capability);
            case T3 -> canT3Access(capability);
        };
    }

    /**
     * Checks if a T1 plugin can access a capability.
     */
    private boolean canT1Access(String capability) {
        return switch (capability) {
            case "config.read", "config.write" -> true;
            default -> false;
        };
    }

    /**
     * Checks if a T2 plugin can access a capability.
     */
    private boolean canT2Access(String capability) {
        return switch (capability) {
            case "config.read", "config.write", "script.execute", "memory.allocate" -> true;
            case "network.access", "file.system" -> false;
            default -> canT1Access(capability);
        };
    }

    /**
     * Checks if a T3 plugin can access a capability.
     */
    private boolean canT3Access(String capability) {
        // T3 plugins have access to all capabilities
        return true;
    }

    /**
     * Validates that a plugin is not attempting tier escalation.
     *
     * @param currentTier the current plugin tier
     * @param requestedCapability the capability being requested
     * @throws PluginTierViolationException if escalation is detected
     */
    public void preventTierEscalation(PluginTier currentTier, String requestedCapability)
            throws PluginTierViolationException {

        if (!canAccessCapability(currentTier, requestedCapability)) {
            throw new PluginTierViolationException(
                String.format("Tier %d plugin cannot access capability: %s",
                    currentTier.getLevel(), requestedCapability));
        }
    }
}
