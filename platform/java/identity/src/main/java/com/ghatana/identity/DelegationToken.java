/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Delegation token recording who delegated what to whom, and the full delegation chain.
 *
 * @param tokenId       opaque identifier
 * @param tenantId      tenant scope
 * @param delegator     the agent granting the delegation
 * @param delegatee     the agent receiving the delegation
 * @param scopes        the delegated scopes
 * @param issuedAt      token issuance time
 * @param expiresAt     token expiry time
 * @param parentTokenId the tokenId of the parent delegation (null if root)
 * @param chain         the full delegation chain from root to this token
 *
 * @doc.type record
 * @doc.purpose Immutable delegation token capturing principal chain for audit
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record DelegationToken(
        String tokenId,
        String tenantId,
        String delegator,
        String delegatee,
        Set<String> scopes,
        Instant issuedAt,
        Instant expiresAt,
        String parentTokenId,
        List<String> chain
) {
    public DelegationToken {
        scopes = Set.copyOf(scopes);
        chain  = List.copyOf(chain);
    }

    /** True if this token has passed its expiry instant. */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
