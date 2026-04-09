/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity.spi;

import com.ghatana.identity.AgentIdentity;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link IdentityResolver} for testing and single-node deployments.
 *
 * <p>Identities are registered via {@link #register(AgentIdentity)} and resolved
 * by composite key {@code tenantId:agentId}. Thread-safe for concurrent use.
 *
 * @doc.type class
 * @doc.purpose Default in-memory identity resolver for testing and dev environments
 * @doc.layer platform
 * @doc.pattern Strategy
 */
public final class InMemoryIdentityResolver implements IdentityResolver {

    private final Map<String, AgentIdentity> registry = new ConcurrentHashMap<>();

    /**
     * Register an identity so it can be resolved.
     *
     * @param identity the identity to register
     */
    public void register(AgentIdentity identity) {
        registry.put(key(identity.tenantId(), identity.agentId()), identity);
    }

    /**
     * Remove a previously registered identity.
     */
    public void deregister(String tenantId, String agentId) {
        registry.remove(key(tenantId, agentId));
    }

    /** Number of registered identities (useful for diagnostics / tests). */
    public int size() {
        return registry.size();
    }

    @Override
    public Promise<Optional<AgentIdentity>> resolve(String tenantId, String agentId) {
        return Promise.of(Optional.ofNullable(registry.get(key(tenantId, agentId))));
    }

    private static String key(String tenantId, String agentId) {
        return tenantId + ':' + agentId;
    }
}
