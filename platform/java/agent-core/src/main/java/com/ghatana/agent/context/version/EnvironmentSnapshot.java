/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.context.version;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Snapshot of the complete environment including dependencies, runtime, and conventions.
 *
 * @doc.type record
 * @doc.purpose Complete environment snapshot
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record EnvironmentSnapshot(
        @NotNull VersionContext versionContext,
        @NotNull DependencyFingerprint dependencyFingerprint,
        @NotNull RuntimeFingerprint runtimeFingerprint,
        @NotNull RepositoryConventionFingerprint repositoryConventionFingerprint,
        @NotNull Instant capturedAt
) {
    public EnvironmentSnapshot {
        Objects.requireNonNull(versionContext, "versionContext must not be null");
        Objects.requireNonNull(dependencyFingerprint, "dependencyFingerprint must not be null");
        Objects.requireNonNull(runtimeFingerprint, "runtimeFingerprint must not be null");
        Objects.requireNonNull(repositoryConventionFingerprint, "repositoryConventionFingerprint must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
    }

    /**
     * Creates an environment snapshot from the current environment.
     *
     * @return environment snapshot
     */
    @NotNull
    public static EnvironmentSnapshot capture() {
        VersionContext versionContext = VersionContext.empty();
        DependencyFingerprint dependencyFingerprint = DependencyFingerprint.fromVersions(Map.of());
        RuntimeFingerprint runtimeFingerprint = RuntimeFingerprint.fromSystem();
        RepositoryConventionFingerprint repositoryConventionFingerprint = RepositoryConventionFingerprint.fromConventions(Map.of());

        return new EnvironmentSnapshot(
                versionContext,
                dependencyFingerprint,
                runtimeFingerprint,
                repositoryConventionFingerprint,
                Instant.now()
        );
    }

    /**
     * Creates an environment snapshot with custom version context.
     *
     * @param versionContext version context
     * @return environment snapshot
     */
    @NotNull
    public static EnvironmentSnapshot captureWithVersionContext(@NotNull VersionContext versionContext) {
        DependencyFingerprint dependencyFingerprint = DependencyFingerprint.fromVersions(versionContext.dependencies());
        RuntimeFingerprint runtimeFingerprint = RuntimeFingerprint.fromSystem();
        RepositoryConventionFingerprint repositoryConventionFingerprint = RepositoryConventionFingerprint.fromConventions(Map.of());

        return new EnvironmentSnapshot(
                versionContext,
                dependencyFingerprint,
                runtimeFingerprint,
                repositoryConventionFingerprint,
                Instant.now()
        );
    }

    /**
     * Computes a combined digest of all fingerprints.
     *
     * @return combined digest
     */
    @NotNull
    public String combinedDigest() {
        return Integer.toHexString(Objects.hash(
                dependencyFingerprint.digest(),
                runtimeFingerprint.digest(),
                repositoryConventionFingerprint.digest()
        ));
    }
}
