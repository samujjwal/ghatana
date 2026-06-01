/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.agent.capability;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * WS9-4: Defines memory scope constraints for agent memory access.
 *
 * <p>Memory scopes control what memory an agent can read/write, including
 * tenant isolation, retention policies, and access patterns.
 *
 * @doc.type record
 * @doc.purpose Agent memory scope definition for governance
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AgentMemoryScope(
        String scopeId,
        String displayName,
        Set<String> readableNamespaces,
        Set<String> writableNamespaces,
        boolean allowCrossTenantRead,
        boolean allowCrossTenantWrite,
        Duration maxRetention,
        boolean requireEncryption,
        Map<String, String> accessConstraints,
        Map<String, String> metadata
) {
    public AgentMemoryScope {
        if (scopeId == null || scopeId.isBlank()) {
            throw new IllegalArgumentException("scopeId must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (readableNamespaces == null) {
            readableNamespaces = Set.of();
        }
        if (writableNamespaces == null) {
            writableNamespaces = Set.of();
        }
        if (accessConstraints == null) {
            accessConstraints = Map.of();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }

    /**
     * Returns true if this scope allows reading from the given namespace.
     */
    public boolean canRead(String namespace) {
        return readableNamespaces.contains(namespace);
    }

    /**
     * Returns true if this scope allows writing to the given namespace.
     */
    public boolean canWrite(String namespace) {
        return writableNamespaces.contains(namespace);
    }

    /**
     * Returns a default memory scope with tenant-isolated access.
     */
    public static AgentMemoryScope defaultScope() {
        return new AgentMemoryScope(
            "default",
            "Default Memory Scope",
            Set.of("agent", "session"),
            Set.of("session"),
            false,
            false,
            Duration.ofDays(30),
            true,
            Map.of(),
            Map.of()
        );
    }
}
