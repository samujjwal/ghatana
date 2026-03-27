/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.Optional;

/**
 * Central service for resolving and managing agent identities.
 *
 * <p>Agents must prove their identity before accessing sensitive operations.
 * The service is SPI-based: swap in a {@link IdentityResolver} implementation
 * to use SPIFFE/SPIRE, VC/DID, or any other identity substrate.
 *
 * <p>All operations are async (ActiveJ {@code Promise}) and must be called
 * from within an ActiveJ eventloop.
 *
 * @doc.type interface
 * @doc.purpose Async API for agent identity resolution, credential issuance and revocation
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface IdentityService {

    /**
     * Resolve the verified identity for {@code agentId} within {@code tenantId}.
     *
     * @param tenantId the tenant scope
     * @param agentId  the agent whose identity to resolve
     * @return a resolved {@link AgentIdentity}, or an empty Optional if unknown
     */
    Promise<Optional<AgentIdentity>> resolve(String tenantId, String agentId);

    /**
     * Issue a short-lived credential token for the given agent.
     *
     * @param tenantId the tenant scope
     * @param agentId  the requesting agent
     * @param ttl      requested token lifetime (service may cap it)
     * @return a signed, time-bounded {@link CredentialToken}
     */
    Promise<CredentialToken> issueCredential(String tenantId, String agentId, Duration ttl);

    /**
     * Revoke a previously issued credential token.
     *
     * @param tokenId the opaque token identifier to revoke
     * @return a promise that completes when revocation is recorded
     */
    Promise<Void> revokeCredential(String tokenId);

    /**
     * Check whether a credential token is still valid (not expired, not revoked).
     *
     * @param tokenId the opaque token identifier
     * @return {@code true} if valid, {@code false} otherwise
     */
    Promise<Boolean> isCredentialValid(String tokenId);
}
