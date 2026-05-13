/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.context.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Version context capturing dependency versions, runtimes, tools, and API contracts.
 *
 * @doc.type record
 * @doc.purpose Version context for mastery applicability
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record VersionContext(
        @NotNull Map<String, String> dependencies,
        @NotNull Map<String, String> runtimes,
        @NotNull Map<String, String> tools,
        @NotNull Map<String, String> apiContracts,
        @NotNull String sourceRef,
        @NotNull Instant resolvedAt
) {
    public VersionContext {
        Objects.requireNonNull(dependencies, "dependencies must not be null");
        Objects.requireNonNull(runtimes, "runtimes must not be null");
        Objects.requireNonNull(tools, "tools must not be null");
        Objects.requireNonNull(apiContracts, "apiContracts must not be null");
        Objects.requireNonNull(sourceRef, "sourceRef must not be null");
        Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");
        dependencies = Map.copyOf(dependencies);
        runtimes = Map.copyOf(runtimes);
        tools = Map.copyOf(tools);
        apiContracts = Map.copyOf(apiContracts);
    }

    /**
     * Creates an empty version context.
     *
     * @return empty version context
     */
    @NotNull
    public static VersionContext empty() {
        return new VersionContext(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                "unknown",
                Instant.now()
        );
    }

    /**
     * Creates a version context from the given parameters.
     *
     * @param dependencies dependency versions
     * @param runtimes runtime versions
     * @param tools tool versions
     * @param apiContracts API contract versions
     * @param sourceRef source reference
     * @param resolvedAt resolution timestamp
     * @return version context
     */
    @NotNull
    public static VersionContext of(
            @NotNull Map<String, String> dependencies,
            @NotNull Map<String, String> runtimes,
            @NotNull Map<String, String> tools,
            @NotNull Map<String, String> apiContracts,
            @NotNull String sourceRef,
            @NotNull Instant resolvedAt) {
        return new VersionContext(dependencies, runtimes, tools, apiContracts, sourceRef, resolvedAt);
    }

    /**
     * Gets the version of a dependency if present.
     *
     * @param packageName package name
     * @return optional version string
     */
    @Nullable
    public String dependencyVersion(@NotNull String packageName) {
        return dependencies.get(packageName);
    }

    /**
     * Returns true if the context has a dependency.
     *
     * @param packageName package name
     * @return true if dependency exists
     */
    public boolean hasDependency(@NotNull String packageName) {
        return dependencies.containsKey(packageName);
    }

    /**
     * Returns true if the context has a runtime.
     *
     * @param runtimeName runtime name
     * @return true if runtime exists
     */
    public boolean hasRuntime(@NotNull String runtimeName) {
        return runtimes.containsKey(runtimeName);
    }

    /**
     * Returns true if the context has a tool.
     *
     * @param toolName tool name
     * @return true if tool exists
     */
    public boolean hasTool(@NotNull String toolName) {
        return tools.containsKey(toolName);
    }
}
