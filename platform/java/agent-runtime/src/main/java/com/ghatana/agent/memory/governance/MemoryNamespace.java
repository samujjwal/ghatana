/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.governance;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Defines an isolated memory namespace with ownership, access control,
 * and governance settings.
 *
 * <p>Memory namespaces provide tenant-level and agent-level isolation for memory
 * storage. All memory operations are scoped to a namespace, and cross-namespace
 * access is governed by explicit sharing rules.
 *
 * @doc.type record
 * @doc.purpose Governed memory namespace with ownership and isolation
 * @doc.layer platform
 * @doc.pattern ValueObject
 * @doc.gaa.memory episodic|semantic|procedural|preference
 */
public record MemoryNamespace(
        /** Unique namespace identifier (e.g., "tenant.procurement.shared"). */
        @NotNull String id,

        /** Tenant that owns this namespace. */
        @NotNull String tenantId,

        /** Agent or agent-group that owns this namespace. */
        @NotNull String ownerId,

        /** Access mode: "private", "shared-read", "shared-write", "public-read". */
        @NotNull String sharingMode,

        /** Whether versioning is enabled for items in this namespace. */
        boolean versioningEnabled,

        /** Conflict resolution policy: "last-write-wins", "version-check", "merge". */
        @NotNull String conflictResolution,

        /** Whether provenance tracking is required for all mutations. */
        boolean provenanceRequired,

        /** Retention period in days (0 = permanent). */
        int retentionDays,

        /** Verification interval in days for fact/knowledge freshness checks. */
        int verificationIntervalDays,

        /** Optional description. */
        @Nullable String description
) {
    public MemoryNamespace {
        Objects.requireNonNull(id, "namespace id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(sharingMode, "sharingMode must not be null");
        Objects.requireNonNull(conflictResolution, "conflictResolution must not be null");
        if (retentionDays < 0) {
            throw new IllegalArgumentException("retentionDays must be non-negative");
        }
    }

    /**
     * Returns {@code true} if the given agent may read from this namespace.
     */
    public boolean canRead(@NotNull String agentId) {
        if (ownerId.equals(agentId)) return true;
        return switch (sharingMode) {
            case "shared-read", "shared-write", "public-read" -> true;
            default -> false;
        };
    }

    /**
     * Returns {@code true} if the given agent may write to this namespace.
     */
    public boolean canWrite(@NotNull String agentId) {
        if (ownerId.equals(agentId)) return true;
        return "shared-write".equals(sharingMode);
    }
}
