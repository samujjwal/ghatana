/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;

/**
 * Version constraint defining applicability of a mastery item.
 *
 * <p>Constraints can specify package versions, tool versions, runtime versions,
 * or framework versions that the skill applies to.
 *
 * @doc.type record
 * @doc.purpose Version constraint for mastery applicability
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record VersionConstraint(
        @NotNull String type,
        @NotNull String constraint,
        @NotNull String description
) {
    public VersionConstraint {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (constraint == null || constraint.isBlank()) {
            throw new IllegalArgumentException("constraint must not be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }

    /**
     * Creates a package version constraint.
     *
     * @param packageName package name (e.g., "react-router")
     * @param versionConstraint version constraint (e.g., ">=6.0.0 <7.0.0")
     * @return version constraint for package
     */
    @NotNull
    public static VersionConstraint packageVersion(@NotNull String packageName, @NotNull String versionConstraint) {
        return new VersionConstraint("package", packageName + "@" + versionConstraint, "Package version: " + packageName);
    }

    /**
     * Creates a tool version constraint.
     *
     * @param toolName tool name (e.g., "node")
     * @param versionConstraint version constraint (e.g., ">=18.0.0")
     * @return version constraint for tool
     */
    @NotNull
    public static VersionConstraint toolVersion(@NotNull String toolName, @NotNull String versionConstraint) {
        return new VersionConstraint("tool", toolName + "@" + versionConstraint, "Tool version: " + toolName);
    }

    /**
     * Creates a runtime version constraint.
     *
     * @param runtimeName runtime name (e.g., "java")
     * @param versionConstraint version constraint (e.g., ">=21")
     * @return version constraint for runtime
     */
    @NotNull
    public static VersionConstraint runtimeVersion(@NotNull String runtimeName, @NotNull String versionConstraint) {
        return new VersionConstraint("runtime", runtimeName + "@" + versionConstraint, "Runtime version: " + runtimeName);
    }
}
