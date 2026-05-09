/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import org.jetbrains.annotations.NotNull;

/**
 * Backend contract for lifecycle phase action defaults and policies.
 *
 * <p>This interface provides backend-owned configuration for phase actions,
 * replacing hardcoded defaults with server-enforced contracts. Implementations
 * should provide environment-specific, tenant-aware defaults based on actual
 * deployment configuration and governance policies.
 *
 * @doc.type interface
 * @doc.purpose Backend contract for lifecycle phase action defaults and policies
 * @doc.layer api
 * @doc.pattern Port
 */
public interface PhaseActionContract {

    /**
     * Gets the default environment for run execution.
     *
     * <p>This should be determined by backend configuration, not hardcoded.
     * Typical values might be "staging", "production", "dev", or "test"
     * based on the deployment context and governance policies.
     *
     * @return the default environment identifier
     */
    @NotNull
    String defaultRunEnvironment();

    /**
     * Gets the default rollback version target.
     *
     * <p>This should be determined by backend deployment tracking,
     * not hardcoded to "previous-stable".
     *
     * @return the default rollback version identifier
     */
    @NotNull
    String defaultRollbackVersion();

    /**
     * Gets the default diff mode for generation review.
     *
     * <p>This should be determined by backend review policy,
     * not hardcoded to "initial-review".
     *
     * @return the default diff mode for generation review
     */
    @NotNull
    String defaultGenerationDiffMode();

    /**
     * Default implementation that provides production-grade defaults.
     * This can be overridden for specific deployment contexts.
     */
    static PhaseActionContract defaults() {
        return new PhaseActionContract() {
            @Override
            public @NotNull String defaultRunEnvironment() {
                // In production, this should come from deployment config
                // For now, using a configurable default instead of hardcoded
                String env = System.getenv("YAPPC_DEFAULT_RUN_ENVIRONMENT");
                return env != null && !env.isBlank() ? env : "staging";
            }

            @Override
            public @NotNull String defaultRollbackVersion() {
                // In production, this should come from deployment tracking
                String version = System.getenv("YAPPC_DEFAULT_ROLLBACK_VERSION");
                return version != null && !version.isBlank() ? version : "previous-stable";
            }

            @Override
            public @NotNull String defaultGenerationDiffMode() {
                // In production, this should come from review policy config
                String mode = System.getenv("YAPPC_DEFAULT_DIFF_MODE");
                return mode != null && !mode.isBlank() ? mode : "initial-review";
            }
        };
    }
}
