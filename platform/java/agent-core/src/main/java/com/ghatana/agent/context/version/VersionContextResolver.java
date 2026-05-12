/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.context.version;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Resolver for determining version context from environment snapshots.
 *
 * @doc.type interface
 * @doc.purpose Resolver for version context
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public interface VersionContextResolver {

    /**
     * Resolves the version context from an environment snapshot.
     *
     * @param snapshot environment snapshot
     * @return promise of version context
     */
    @NotNull
    Promise<VersionContext> resolve(@NotNull EnvironmentSnapshot snapshot);

    /**
     * Resolves the version context from dependency and runtime fingerprints.
     *
     * @param dependencyFingerprint dependency fingerprint
     * @param runtimeFingerprint runtime fingerprint
     * @return promise of version context
     */
    @NotNull
    Promise<VersionContext> resolveFromFingerprints(
            @NotNull DependencyFingerprint dependencyFingerprint,
            @NotNull RuntimeFingerprint runtimeFingerprint
    );
}
