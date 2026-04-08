/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.memory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable record representing a registered memory namespace.
 *
 * <p>A memory namespace defines the isolation scope and policy for a set of
 * agent memories belonging to a specific agent and tenant. Namespaces are
 * typed by {@link MemoryScope} and can be configured with retention and
 * promotion policies.
 *
 * @param namespaceId      globally unique namespace identifier
 * @param tenantId         tenant scope
 * @param agentId          the agent that owns this namespace
 * @param scope            memory type (EPISODIC, SEMANTIC, PROCEDURAL, PREFERENCE)
 * @param label            human-readable name for the namespace
 * @param description      optional description
 * @param retentionDays    optional retention period in days (null = indefinite)
 * @param promotionEnabled whether memories in this namespace can be promoted
 * @param maxEntries       optional cap on the number of memory entries
 * @param createdAt        namespace registration timestamp
 * @param updatedAt        last update timestamp
 * @param data             additional structured metadata
 *
 * @doc.type class
 * @doc.purpose Immutable memory namespace descriptor for agent memory isolation
 * @doc.layer framework
 * @doc.pattern Value Object
 */
public record MemoryNamespace(
        @NotNull String namespaceId,
        @NotNull String tenantId,
        @NotNull String agentId,
        @NotNull MemoryScope scope,
        @NotNull String label,
        @Nullable String description,
        @Nullable Integer retentionDays,
        boolean promotionEnabled,
        @Nullable Integer maxEntries,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt,
        @NotNull Map<String, Object> data
) {

    /** Validates required fields and normalises the data map to an immutable copy. */
    public MemoryNamespace {
        Objects.requireNonNull(namespaceId, "namespaceId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(data, "data");

        if (namespaceId.isBlank()) throw new IllegalArgumentException("namespaceId must not be blank");
        if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (agentId.isBlank()) throw new IllegalArgumentException("agentId must not be blank");
        if (label.isBlank()) throw new IllegalArgumentException("label must not be blank");
        if (retentionDays != null && retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be positive");
        }
        if (maxEntries != null && maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }

        data = Map.copyOf(data);
    }

    /**
     * Creates a minimal namespace with no retention limits and promotion disabled.
     *
     * @param namespaceId unique identifier
     * @param tenantId    tenant scope
     * @param agentId     owning agent
     * @param scope       memory scope
     * @param label       human-readable name
     * @param now         creation and update timestamp
     * @return a new {@code MemoryNamespace}
     */
    public static MemoryNamespace of(
            String namespaceId,
            String tenantId,
            String agentId,
            MemoryScope scope,
            String label,
            Instant now) {
        return new MemoryNamespace(namespaceId, tenantId, agentId, scope, label,
                null, null, false, null, now, now, Map.of());
    }
}
