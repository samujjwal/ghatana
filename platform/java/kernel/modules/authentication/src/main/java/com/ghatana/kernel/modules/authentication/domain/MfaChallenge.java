/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.authentication.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a multi-factor authentication challenge.
 *
 * @doc.type record
 * @doc.purpose MFA challenge data structure
 * @doc.layer kernel
 * @doc.pattern Domain
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public record MfaChallenge(
    String challengeId,
    String tenantId,
    String principalId,
    String mfaType,
    String challengeCode,
    Instant createdAt,
    Instant expiresAt
) {
    public MfaChallenge {
        Objects.requireNonNull(challengeId, "challengeId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(mfaType, "mfaType");
        Objects.requireNonNull(challengeCode, "challengeCode");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        
        if (expiresAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    /**
     * Checks if the challenge has expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if the challenge is valid (not expired).
     *
     * @return true if valid
     */
    public boolean isValid() {
        return !isExpired();
    }
}
