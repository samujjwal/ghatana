/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

import java.time.Instant;
import java.util.Set;

/**
 * Immutable container for decoded JWT claims.
 *
 * @param tokenId    opaque unique identifier for this token
 * @param tenantId   tenant scope from claim
 * @param agentId    agent ID from claim
 * @param scopes     granted scopes from claim
 * @param issuedAt   iat claim
 * @param expiresAt  exp claim
 * @param notBefore  nbf claim (optional, token not valid before this)
 *
 * @doc.type record
 * @doc.purpose Immutable decoded JWT claims
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record TokenClaims(
        String tokenId,
        String tenantId,
        String agentId,
        Set<String> scopes,
        Instant issuedAt,
        Instant expiresAt,
        Instant notBefore
) {
    public TokenClaims {
        scopes = Set.copyOf(scopes);
    }

    /** True if this token is currently valid (not before notBefore, not after expiresAt). */
    public boolean isValid() {
        Instant now = Instant.now();
        return !now.isBefore(notBefore) && now.isBefore(expiresAt);
    }

    /** True if this token has passed its expiration time. */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /** True if this token has not yet become valid (before notBefore). */
    public boolean isNotYetValid() {
        return Instant.now().isBefore(notBefore);
    }
}
