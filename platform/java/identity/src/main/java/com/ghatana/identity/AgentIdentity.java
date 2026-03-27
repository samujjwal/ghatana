/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

import java.time.Instant;
import java.util.Set;

/**
 * Immutable record representing a verified agent identity.
 *
 * @param tenantId   tenant that owns this identity
 * @param agentId    unique agent identifier within the tenant
 * @param spiffeId   SPIFFE SVID URI (e.g. {@code spiffe://ghatana.io/tenant/t1/agent/a1});
 *                   {@code null} when not backed by SPIFFE
 * @param scopes     set of granted permission scopes
 * @param verifiedAt when this identity was last cryptographically verified
 *
 * @doc.type record
 * @doc.purpose Immutable verified agent identity with SPIFFE support
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record AgentIdentity(
        String tenantId,
        String agentId,
        String spiffeId,
        Set<String> scopes,
        Instant verifiedAt
) {
    public AgentIdentity {
        scopes = Set.copyOf(scopes);
    }

    /** True if this identity carries the given scope. */
    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}
