/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.context.version;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op implementation of {@link VersionContextResolver} for production use.
 *
 * <p>This implementation returns an empty {@link VersionContext} for all requests.
 * A full implementation would analyze environment snapshots, dependency fingerprints,
 * and runtime fingerprints to determine the actual version context of the execution environment.
 *
 * <p>This no-op implementation is safe because it defaults to {@link VersionContext#empty()},
 * which means version-aware dispatch will treat all versions as unknown and apply
 * conservative safety policies (e.g., verification-first, human-gated).
 *
 * @doc.type class
 * @doc.purpose No-op version context resolver for production
 * @doc.layer agent-core
 * @doc.pattern Null Object
 */
public final class DefaultVersionContextResolver implements VersionContextResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultVersionContextResolver.class);
    private static final DefaultVersionContextResolver INSTANCE = new DefaultVersionContextResolver();

    private DefaultVersionContextResolver() {
        // Private constructor for singleton
    }

    /**
     * Returns the singleton instance.
     *
     * @return the version context resolver instance
     */
    @NotNull
    public static DefaultVersionContextResolver getInstance() {
        return INSTANCE;
    }

    /**
     * Resolves the version context from an environment snapshot.
     *
     * <p>This no-op implementation always returns {@link VersionContext#empty()}.
     * A full implementation would analyze the environment snapshot to determine
     * the actual version context.
     *
     * @param snapshot environment snapshot (ignored in this no-op implementation)
     * @return promise of empty version context
     */
    @Override
    @NotNull
    public Promise<VersionContext> resolve(@NotNull EnvironmentSnapshot snapshot) {
        log.debug("DefaultVersionContextResolver returning empty VersionContext");
        return Promise.of(VersionContext.empty());
    }

    /**
     * Resolves the version context from dependency and runtime fingerprints.
     *
     * <p>This no-op implementation always returns {@link VersionContext#empty()}.
     * A full implementation would analyze the fingerprints to determine
     * the actual version context.
     *
     * @param dependencyFingerprint dependency fingerprint (ignored in this no-op implementation)
     * @param runtimeFingerprint runtime fingerprint (ignored in this no-op implementation)
     * @return promise of empty version context
     */
    @Override
    @NotNull
    public Promise<VersionContext> resolveFromFingerprints(
            @NotNull DependencyFingerprint dependencyFingerprint,
            @NotNull RuntimeFingerprint runtimeFingerprint) {
        log.debug("DefaultVersionContextResolver returning empty VersionContext from fingerprints");
        return Promise.of(VersionContext.empty());
    }
}
