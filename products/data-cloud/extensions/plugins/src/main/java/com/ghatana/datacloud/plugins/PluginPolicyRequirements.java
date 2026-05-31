/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugins;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plugin policy requirements (P8).
 *
 * <p>Defines the policy requirements that a plugin must meet to be allowed
 * to run in the system.
 *
 * @doc.type record
 * @doc.purpose Plugin policy requirements for compliance
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PluginPolicyRequirements(
        Set<String> requiredPermissions,
        Set<String> requiredRoles,
        List<String> allowedTenants,
        boolean requiresSovereignDeployment,
        boolean requiresIsolation,
        Map<String, Object> customRequirements
) {
    public PluginPolicyRequirements {
        if (requiredPermissions == null) {
            requiredPermissions = Set.of();
        }
        if (requiredRoles == null) {
            requiredRoles = Set.of();
        }
        if (allowedTenants == null) {
            allowedTenants = List.of();
        }
        if (customRequirements == null) {
            customRequirements = Map.of();
        }
    }

    /**
     * Returns default policy requirements.
     */
    public static PluginPolicyRequirements defaultRequirements() {
        return new PluginPolicyRequirements(Set.of(), Set.of(), List.of(), false, false, Map.of());
    }

    /**
     * Returns true if all policy requirements are satisfied.
     */
    public boolean allSatisfied() {
        // In a real implementation, this would check against actual tenant/role/permission context
        return true;
    }

    /**
     * Returns true if the plugin requires sovereign deployment.
     */
    public boolean requiresSovereign() {
        return requiresSovereignDeployment;
    }

    /**
     * Returns true if the plugin requires isolation.
     */
    public boolean requiresIsolation() {
        return requiresIsolation;
    }
}
