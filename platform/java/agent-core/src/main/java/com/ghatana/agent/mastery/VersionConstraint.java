/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;

/**
 * Version constraint defining applicability of a mastery item.
 *
 * <p>Constraints specify package versions, tool versions, runtime versions,
 * or framework versions that the skill applies to.
 *
 * @doc.type record
 * @doc.purpose Version constraint for mastery applicability
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record VersionConstraint(
        @NotNull String kind,
        @NotNull String name,
        @NotNull String range,
        @NotNull String ecosystem
) {
    public VersionConstraint {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("kind must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (range == null || range.isBlank()) {
            throw new IllegalArgumentException("range must not be blank");
        }
        if (ecosystem == null || ecosystem.isBlank()) {
            throw new IllegalArgumentException("ecosystem must not be blank");
        }
    }

    /**
     * Creates a package version constraint.
     *
     * @param packageName package name (e.g., "react-router")
     * @param versionRange version range (e.g., ">=6.0.0 <7.0.0")
     * @param ecosystem ecosystem (e.g., "npm", "maven")
     * @return version constraint for package
     */
    @NotNull
    public static VersionConstraint packageVersion(@NotNull String packageName, @NotNull String versionRange, @NotNull String ecosystem) {
        return new VersionConstraint("package", packageName, versionRange, ecosystem);
    }

    /**
     * Creates a tool version constraint.
     *
     * @param toolName tool name (e.g., "node")
     * @param versionRange version range (e.g., ">=18.0.0")
     * @param ecosystem ecosystem (e.g., "system", "container")
     * @return version constraint for tool
     */
    @NotNull
    public static VersionConstraint toolVersion(@NotNull String toolName, @NotNull String versionRange, @NotNull String ecosystem) {
        return new VersionConstraint("tool", toolName, versionRange, ecosystem);
    }

    /**
     * Creates a runtime version constraint.
     *
     * @param runtimeName runtime name (e.g., "java")
     * @param versionRange version range (e.g., ">=21")
     * @param ecosystem ecosystem (e.g., "jvm", "native")
     * @return version constraint for runtime
     */
    @NotNull
    public static VersionConstraint runtimeVersion(@NotNull String runtimeName, @NotNull String versionRange, @NotNull String ecosystem) {
        return new VersionConstraint("runtime", runtimeName, versionRange, ecosystem);
    }
}
