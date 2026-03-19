/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.authentication.domain;

import java.util.Objects;

/**
 * Represents a multi-factor authentication verification request.
 *
 * @doc.type record
 * @doc.purpose MFA verification request data structure
 * @doc.layer kernel
 * @doc.pattern Domain
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public record MfaVerification(
    String challengeId,
    String tenantId,
    String principalId,
    String mfaType,
    String response
) {
    public MfaVerification {
        Objects.requireNonNull(challengeId, "challengeId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(mfaType, "mfaType");
        Objects.requireNonNull(response, "response");
        
        if (response.trim().isEmpty()) {
            throw new IllegalArgumentException("response cannot be empty");
        }
    }
}
