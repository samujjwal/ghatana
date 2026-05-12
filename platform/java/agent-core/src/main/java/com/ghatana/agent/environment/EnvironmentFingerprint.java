/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.environment;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fingerprint of the execution environment for version-aware mastery matching.
 *
 * <p>Contains tenant, repository, project type, dependencies, tools, runtimes,
 * frameworks, conventions, and project files to determine which mastery items are applicable.
 *
 * @doc.type record
 * @doc.purpose Environment fingerprint for version-aware mastery
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record EnvironmentFingerprint(
        @NotNull String tenantId,
        @NotNull String repoId,
        @NotNull String projectType,
        @NotNull Map<String, String> dependencies,
        @NotNull Map<String, String> tools,
        @NotNull Map<String, String> runtimes,
        @NotNull Map<String, String> frameworks,
        @NotNull Map<String, String> conventions,
        @NotNull Map<String, String> projectFiles,
        @NotNull Instant observedAt,
        @NotNull List<String> evidenceRefs
) {
    public EnvironmentFingerprint {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(repoId, "repoId must not be null");
        Objects.requireNonNull(projectType, "projectType must not be null");
        Objects.requireNonNull(dependencies, "dependencies must not be null");
        Objects.requireNonNull(tools, "tools must not be null");
        Objects.requireNonNull(runtimes, "runtimes must not be null");
        Objects.requireNonNull(frameworks, "frameworks must not be null");
        Objects.requireNonNull(conventions, "conventions must not be null");
        Objects.requireNonNull(projectFiles, "projectFiles must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        Objects.requireNonNull(evidenceRefs, "evidenceRefs must not be null");
        dependencies = Map.copyOf(dependencies);
        tools = Map.copyOf(tools);
        runtimes = Map.copyOf(runtimes);
        frameworks = Map.copyOf(frameworks);
        conventions = Map.copyOf(conventions);
        projectFiles = Map.copyOf(projectFiles);
        evidenceRefs = List.copyOf(evidenceRefs);
    }

    /**
     * Creates a minimal environment fingerprint.
     *
     * @param tenantId tenant identifier
     * @param repoId repository identifier
     * @param projectType project type (e.g., "typescript", "java")
     * @return minimal environment fingerprint
     */
    @NotNull
    public static EnvironmentFingerprint minimal(
            @NotNull String tenantId,
            @NotNull String repoId,
            @NotNull String projectType
    ) {
        return new EnvironmentFingerprint(
                tenantId,
                repoId,
                projectType,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Instant.now(),
                List.of()
        );
    }

    /**
     * Returns a canonical string representation of this fingerprint for comparison.
     *
     * @return canonical string representation
     */
    @NotNull
    public String canonicalString() {
        return String.join("|",
                tenantId,
                repoId,
                projectType,
                dependencies.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(java.util.stream.Collectors.joining(",")),
                tools.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(java.util.stream.Collectors.joining(",")),
                runtimes.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(java.util.stream.Collectors.joining(","))
        );
    }
}
