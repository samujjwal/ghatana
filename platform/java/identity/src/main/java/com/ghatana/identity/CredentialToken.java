/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

import java.time.Instant;

/**
 * Short-lived signed credential token issued by {@link IdentityService}.
 *
 * @param tokenId   opaque unique identifier (used for revocation lookups)
 * @param agentId   agent the token was issued for
 * @param tenantId  tenant scope
 * @param issuedAt  issuance timestamp
 * @param expiresAt expiry timestamp (service enforces hard limit)
 * @param signedJwt the compact JWT string to embed in API calls
 *
 * @doc.type record
 * @doc.purpose Immutable time-bounded credential token for agent authentication
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record CredentialToken(
        String tokenId,
        String agentId,
        String tenantId,
        Instant issuedAt,
        Instant expiresAt,
        String signedJwt
) {
    /** True if this token has passed its expiry instant. */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
