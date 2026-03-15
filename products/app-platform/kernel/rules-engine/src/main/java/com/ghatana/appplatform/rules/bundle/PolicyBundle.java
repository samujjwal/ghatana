/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.rules.bundle;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents an OPA policy bundle: a versioned collection of Rego files and data files (K03-002).
 *
 * <p>A bundle is immutable once created. The {@link #sha256Hash()} is computed at upload time
 * and verified before every use to detect storage corruption.
 *
 * <p>OPA polls for the active bundle using the OPA bundle API. Only one bundle is active at
 * a time per policy scope.
 *
 * @doc.type record
 * @doc.purpose OPA policy bundle value object with SHA-256 integrity hash
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PolicyBundle(
        String bundleId,
        String name,
        int version,
        String sha256Hash,   // hex-encoded SHA-256 of raw bundle content
        byte[] content,      // raw bundle bytes (tar.gz of Rego + data files)
        boolean active,
        Instant uploadedAt
) {
    public PolicyBundle {
        Objects.requireNonNull(bundleId, "bundleId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sha256Hash, "sha256Hash");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(uploadedAt, "uploadedAt");
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
    }

    /**
     * Returns a copy of this bundle with {@code active} set to {@code true}.
     */
    public PolicyBundle activated() {
        return new PolicyBundle(bundleId, name, version, sha256Hash, content, true, uploadedAt);
    }

    /**
     * Returns a copy of this bundle with {@code active} set to {@code false}.
     */
    public PolicyBundle deactivated() {
        return new PolicyBundle(bundleId, name, version, sha256Hash, content, false, uploadedAt);
    }
}
