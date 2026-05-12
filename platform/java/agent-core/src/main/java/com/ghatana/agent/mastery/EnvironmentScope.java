/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Environment scope defining which environments a mastery item applies to.
 *
 * @doc.type record
 * @doc.purpose Environment scope for mastery applicability
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record EnvironmentScope(
        @NotNull Set<String> productIds,
        @NotNull Set<String> projectTypes,
        @NotNull Set<String> frameworks,
        @NotNull Set<String> runtimes,
        @NotNull Set<String> repoConventions,
        @NotNull Set<String> incompatibleWith
) {
    public EnvironmentScope {
        Objects.requireNonNull(productIds, "productIds must not be null");
        Objects.requireNonNull(projectTypes, "projectTypes must not be null");
        Objects.requireNonNull(frameworks, "frameworks must not be null");
        Objects.requireNonNull(runtimes, "runtimes must not be null");
        Objects.requireNonNull(repoConventions, "repoConventions must not be null");
        Objects.requireNonNull(incompatibleWith, "incompatibleWith must not be null");
        productIds = Set.copyOf(productIds);
        projectTypes = Set.copyOf(projectTypes);
        frameworks = Set.copyOf(frameworks);
        runtimes = Set.copyOf(runtimes);
        repoConventions = Set.copyOf(repoConventions);
        incompatibleWith = Set.copyOf(incompatibleWith);
    }

    /**
     * Creates an empty environment scope with no constraints.
     *
     * @return empty environment scope
     */
    @NotNull
    public static EnvironmentScope empty() {
        return new EnvironmentScope(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    /**
     * Checks if this environment scope matches the given environment snapshot.
     *
     * @param snapshot environment snapshot to check
     * @return true if this scope matches the snapshot
     */
    public boolean matches(@NotNull Map<String, String> snapshot) {
        // Check incompatible constraints first
        for (String incompatible : incompatibleWith) {
            if (snapshot.containsKey(incompatible)) {
                return false;
            }
        }

        // Check product IDs
        if (!productIds.isEmpty() && !productIds.contains(snapshot.get("productId"))) {
            return false;
        }

        // Check project types
        if (!projectTypes.isEmpty() && !projectTypes.contains(snapshot.get("projectType"))) {
            return false;
        }

        // Check frameworks
        if (!frameworks.isEmpty()) {
            boolean frameworkMatch = frameworks.stream()
                    .anyMatch(fw -> snapshot.containsKey("framework") && snapshot.get("framework").equals(fw));
            if (!frameworkMatch) {
                return false;
            }
        }

        // Check runtimes
        if (!runtimes.isEmpty()) {
            boolean runtimeMatch = runtimes.stream()
                    .anyMatch(rt -> snapshot.containsKey("runtime") && snapshot.get("runtime").equals(rt));
            if (!runtimeMatch) {
                return false;
            }
        }

        // Check repo conventions
        if (!repoConventions.isEmpty()) {
            boolean conventionMatch = repoConventions.stream()
                    .anyMatch(conv -> snapshot.containsKey("repoConvention") && snapshot.get("repoConvention").equals(conv));
            if (!conventionMatch) {
                return false;
            }
        }

        return true;
    }
}
