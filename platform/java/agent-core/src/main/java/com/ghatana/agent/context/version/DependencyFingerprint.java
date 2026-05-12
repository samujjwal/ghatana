/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.context.version;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * Fingerprint of dependency versions for a specific context.
 *
 * @doc.type record
 * @doc.purpose Fingerprint of dependency versions
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record DependencyFingerprint(
        @NotNull Map<String, String> packageVersions,
        @NotNull String digest
) {
    public DependencyFingerprint {
        Objects.requireNonNull(packageVersions, "packageVersions must not be null");
        Objects.requireNonNull(digest, "digest must not be null");
        packageVersions = Map.copyOf(packageVersions);
    }

    /**
     * Creates a dependency fingerprint from package versions.
     *
     * @param packageVersions package versions
     * @return dependency fingerprint
     */
    @NotNull
    public static DependencyFingerprint fromVersions(@NotNull Map<String, String> packageVersions) {
        String digest = computeDigest(packageVersions);
        return new DependencyFingerprint(packageVersions, digest);
    }

    /**
     * Computes a digest from package versions.
     *
     * @param packageVersions package versions
     * @return digest string
     */
    @NotNull
    private static String computeDigest(@NotNull Map<String, String> packageVersions) {
        return Integer.toHexString(Objects.hash(packageVersions));
    }

    /**
     * Returns true if this fingerprint matches another.
     *
     * @param other other fingerprint
     * @return true if digests match
     */
    public boolean matches(@NotNull DependencyFingerprint other) {
        return this.digest.equals(other.digest);
    }
}
