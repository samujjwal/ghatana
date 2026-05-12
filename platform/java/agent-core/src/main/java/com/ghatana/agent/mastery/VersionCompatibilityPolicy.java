/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Policy defining version compatibility requirements for mastery items.
 *
 * <p>Version compatibility policies determine which versions of dependencies,
 * runtimes, and API contracts a mastery item is compatible with.
 *
 * @doc.type record
 * @doc.purpose Policy defining version compatibility requirements for mastery items
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record VersionCompatibilityPolicy(
        @NotNull String policyId,
        @NotNull VersionScope versionScope,
        boolean strictMode,
        boolean allowMinorVersionDrift,
        boolean allowPatchVersionDrift
) {
    public VersionCompatibilityPolicy {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(versionScope, "versionScope must not be null");
    }

    /**
     * Creates a default version compatibility policy.
     *
     * @param policyId the policy identifier
     * @return default version compatibility policy
     */
    @NotNull
    public static VersionCompatibilityPolicy defaultPolicy(@NotNull String policyId) {
        return new VersionCompatibilityPolicy(
                policyId,
                new VersionScope(java.util.List.of(), java.util.List.of(), java.util.List.of()),
                false,
                true,
                true
        );
    }

    /**
     * Creates a strict version compatibility policy that requires exact version matches.
     *
     * @param policyId the policy identifier
     * @param versionScope the version scope to enforce
     * @return strict version compatibility policy
     */
    @NotNull
    public static VersionCompatibilityPolicy strictPolicy(
            @NotNull String policyId,
            @NotNull VersionScope versionScope) {
        return new VersionCompatibilityPolicy(
                policyId,
                versionScope,
                true,
                false,
                false
        );
    }
}
