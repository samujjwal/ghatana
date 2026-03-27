/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

import io.activej.promise.Promise;

import java.time.Duration;

/**
 * Issues cryptographically bound delegation tokens that represent one agent acting
 * on behalf of another, with explicit scope and duration constraints.
 *
 * <p>Delegation tokens carry the full principal chain so that audit records can
 * reconstruct "Agent A delegated to Agent B delegated to Agent C" for a given action.
 *
 * @doc.type interface
 * @doc.purpose Issue, validate and chain delegation tokens between agents
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface DelegationTokenService {

    /**
     * Issue a delegation token granting {@code delegateeId} the right to act for
     * {@code delegatorId} within the specified scopes.
     *
     * @param tenantId    tenant scope
     * @param delegatorId the agent that is delegating
     * @param delegateeId the agent being delegated to
     * @param scopes      the specific scopes being delegated (must be a subset of delegator's own scopes)
     * @param ttl         how long the delegation is valid
     * @return a signed {@link DelegationToken}
     */
    Promise<DelegationToken> delegate(String tenantId,
                                      String delegatorId,
                                      String delegateeId,
                                      java.util.Set<String> scopes,
                                      Duration ttl);

    /**
     * Validate an existing delegation token.
     *
     * @param tokenId the opaque token identifier
     * @return the token if valid and not revoked, empty otherwise
     */
    Promise<java.util.Optional<DelegationToken>> validate(String tokenId);

    /**
     * Revoke a delegation token.
     *
     * @param tokenId the token to revoke
     */
    Promise<Void> revoke(String tokenId);
}
