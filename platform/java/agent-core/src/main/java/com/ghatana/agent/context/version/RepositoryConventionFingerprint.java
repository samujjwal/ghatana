/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.context.version;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * Fingerprint of repository conventions and conventions used in the codebase.
 *
 * @doc.type record
 * @doc.purpose Fingerprint of repository conventions
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record RepositoryConventionFingerprint(
        @NotNull Map<String, String> conventions,
        @NotNull String digest
) {
    public RepositoryConventionFingerprint {
        Objects.requireNonNull(conventions, "conventions must not be null");
        Objects.requireNonNull(digest, "digest must not be null");
        conventions = Map.copyOf(conventions);
    }

    /**
     * Creates a repository convention fingerprint.
     *
     * @param conventions map of convention names to values
     * @return repository convention fingerprint
     */
    @NotNull
    public static RepositoryConventionFingerprint fromConventions(@NotNull Map<String, String> conventions) {
        String digest = computeDigest(conventions);
        return new RepositoryConventionFingerprint(conventions, digest);
    }

    /**
     * Computes a digest from conventions.
     *
     * @param conventions conventions map
     * @return digest string
     */
    @NotNull
    private static String computeDigest(@NotNull Map<String, String> conventions) {
        return Integer.toHexString(Objects.hash(conventions));
    }

    /**
     * Returns true if this fingerprint matches another.
     *
     * @param other other fingerprint
     * @return true if digests match
     */
    public boolean matches(@NotNull RepositoryConventionFingerprint other) {
        return this.digest.equals(other.digest);
    }
}
