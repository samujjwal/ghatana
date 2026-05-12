/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.mastery.VersionScope;
import org.jetbrains.annotations.NotNull;

/**
 * Policy for version-aware memory queries that filters based on version compatibility.
 *
 * @doc.type class
 * @doc.purpose Version-aware memory query policy
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public final class VersionAwareMemoryQueryPolicy {

    /**
     * Determines if memory should be queried based on version compatibility.
     *
     * @param versionScope version scope of the mastery item
     * @param versionContext current version context
     * @return true if query is allowed
     */
    public static boolean shouldQuery(
            @NotNull VersionScope versionScope,
            @NotNull VersionContext versionContext) {
        // Check if current version is in active scope
        if (!versionScope.supportsActive(versionContext)) {
            return false;
        }

        // Check version compatibility
        for (var constraint : versionScope.active()) {
            String packageName = constraint.type();
            String requiredVersion = constraint.constraint();

            String currentVersion = versionContext.dependencies().get(packageName);
            if (currentVersion != null && !isVersionCompatible(currentVersion, requiredVersion)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Simple version compatibility check.
     *
     * @param current current version
     * @param required required version
     * @return true if compatible
     */
    private static boolean isVersionCompatible(String current, String required) {
        // Simple implementation - in production would use proper version comparison
        if (required.startsWith(">=")) {
            return compareVersions(current, required.substring(2)) >= 0;
        }
        if (required.startsWith("<=")) {
            return compareVersions(current, required.substring(2)) <= 0;
        }
        return current.equals(required);
    }

    /**
     * Simple version comparison.
     *
     * @param v1 version 1
     * @param v2 version 2
     * @return comparison result
     */
    private static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        for (int i = 0; i < Math.max(parts1.length, parts2.length); i++) {
            int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (p1 != p2) {
                return p1 - p2;
            }
        }
        return 0;
    }
}
