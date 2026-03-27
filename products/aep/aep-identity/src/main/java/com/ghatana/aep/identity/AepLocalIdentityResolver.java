/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.identity;

import com.ghatana.identity.AgentIdentity;
import com.ghatana.identity.spi.IdentityResolver;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AEP-scoped {@link IdentityResolver} that maintains a local registry of AEP
 * agent identities pre-populated from the AEP engine's agent registry.
 *
 * <p>Intended to be the first resolver in the chain, before any remote fallback.
 * Agents register themselves at startup via {@link #register} and deregister
 * during clean shutdown via {@link #deregister}.
 *
 * @doc.type class
 * @doc.purpose AEP engine-local identity resolver backed by the agent registry
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class AepLocalIdentityResolver implements IdentityResolver {

    private final Map<String, AgentIdentity> registry = new ConcurrentHashMap<>();

    /**
     * Register an agent identity in the local resolver.
     *
     * @param identity the agent identity to register
     */
    public void register(AgentIdentity identity) {
        registry.put(key(identity.tenantId(), identity.agentId()), identity);
    }

    /**
     * Deregister an agent identity (e.g. on graceful shutdown).
     *
     * @param tenantId the tenant-scoped agent namespace
     * @param agentId  the agent to deregister
     */
    public void deregister(String tenantId, String agentId) {
        registry.remove(key(tenantId, agentId));
    }

    /** Returns the number of registered agent identities. */
    public int size() {
        return registry.size();
    }

    @Override
    public boolean supports(String tenantId, String agentId) {
        return registry.containsKey(key(tenantId, agentId));
    }

    @Override
    public Promise<Optional<AgentIdentity>> resolve(String tenantId, String agentId) {
        return Promise.of(Optional.ofNullable(registry.get(key(tenantId, agentId))));
    }

    private static String key(String tenantId, String agentId) {
        return tenantId + ":" + agentId;
    }
}
