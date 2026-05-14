/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents a change in a dependency that may cause obsolescence.
 *
 * @doc.type record
 * @doc.purpose Value object for dependency changes
 * @doc.layer agent-core
 * @doc.pattern Value Object
 */
public record DependencyChange(
        @NotNull String dependencyName,
        @NotNull String previousVersion,
        @NotNull String newVersion,
        @NotNull ChangeType changeType,
        @NotNull String description
) {
    public DependencyChange {
        Objects.requireNonNull(dependencyName, "dependencyName must not be null");
        Objects.requireNonNull(previousVersion, "previousVersion must not be null");
        Objects.requireNonNull(newVersion, "newVersion must not be null");
        Objects.requireNonNull(changeType, "changeType must not be null");
        Objects.requireNonNull(description, "description must not be null");
    }

    /**
     * Types of dependency changes.
     */
    public enum ChangeType {
        MAJOR_VERSION_BUMP,
        MINOR_VERSION_BUMP,
        PATCH_VERSION_BUMP,
        DEPRECATED,
        REMOVED,
        SECURITY_FIX,
        BREAKING_CHANGE
    }

    /**
     * Creates a dependency change for a version bump.
     */
    @NotNull
    public static DependencyChange versionBump(
            @NotNull String dependencyName,
            @NotNull String previousVersion,
            @NotNull String newVersion,
            @NotNull ChangeType changeType) {
        return new DependencyChange(
                dependencyName,
                previousVersion,
                newVersion,
                changeType,
                "Version bump from " + previousVersion + " to " + newVersion
        );
    }

    /**
     * Creates a dependency change for a deprecated dependency.
     */
    @NotNull
    public static DependencyChange deprecated(
            @NotNull String dependencyName,
            @NotNull String version,
            @NotNull String reason) {
        return new DependencyChange(
                dependencyName,
                version,
                version,
                ChangeType.DEPRECATED,
                reason
        );
    }

    /**
     * Creates a dependency change for a removed dependency.
     */
    @NotNull
    public static DependencyChange removed(
            @NotNull String dependencyName,
            @NotNull String version,
            @NotNull String reason) {
        return new DependencyChange(
                dependencyName,
                version,
                version,
                ChangeType.REMOVED,
                reason
        );
    }
}
