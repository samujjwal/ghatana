/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity.spi;

import com.ghatana.identity.AgentIdentity;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * SPI for pluggable agent identity resolution backends.
 *
 * <p>Implementations may integrate SPIFFE/SPIRE, VC/DID, OAuth2 token introspection,
 * or a simple in-memory registry for testing. The default implementation used by
 * {@link com.ghatana.identity.DefaultIdentityService} is
 * {@link InMemoryIdentityResolver}.
 *
 * @doc.type interface
 * @doc.purpose SPI for pluggable identity resolution (SPIFFE, DID, in-memory, etc.)
 * @doc.layer platform
 * @doc.pattern Strategy
 */
public interface IdentityResolver {

    /**
     * Resolve the identity for the given agent.
     *
     * @param tenantId the tenant scope
     * @param agentId  the agent whose identity to resolve
     * @return the resolved identity, or empty if not found / not trusted
     */
    Promise<Optional<AgentIdentity>> resolve(String tenantId, String agentId);

    /**
     * Returns true if this resolver can handle the given agent within the tenant.
     * Used by composite resolvers to delegate to the right backend.
     */
    default boolean supports(String tenantId, String agentId) {
        return true;
    }
}
