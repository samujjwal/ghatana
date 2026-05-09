package com.ghatana.platform.domain.eventstore;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Tenant context value object for event-store operations.
 *
 * <p><b>DC-17:</b> Canonical TenantWorkspaceContext with tenant ID, optional workspace ID,
 * and metadata. This context is propagated across API/storage/audit/events/AI/plugins layers
 * to ensure consistent tenant and workspace scoping throughout the system.
 *
 * @doc.type record
 * @doc.purpose Multi-tenant context for platform-level event-store contracts
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record TenantContext(
    String tenantId,
    Optional<String> workspaceId,
    Map<String, String> metadata
) {
    public TenantContext {
        Objects.requireNonNull(tenantId, "tenantId required");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        workspaceId = workspaceId != null ? workspaceId : Optional.empty();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public static TenantContext of(String tenantId) {
        return new TenantContext(tenantId, Optional.empty(), Map.of());
    }

    public static TenantContext of(String tenantId, String workspaceId) {
        return new TenantContext(tenantId, Optional.ofNullable(workspaceId), Map.of());
    }

    public static TenantContext of(String tenantId, Map<String, String> metadata) {
        return new TenantContext(tenantId, Optional.empty(), metadata);
    }

    /**
     * Returns a new {@code TenantContext} with the given metadata key-value pair added.
     *
     * @param key   metadata key
     * @param value metadata value
     * @return new TenantContext with the additional metadata entry
     */
    public TenantContext withMetadata(String key, String value) {
        Objects.requireNonNull(key, "key required");
        Objects.requireNonNull(value, "value required");
        Map<String, String> updated = new java.util.HashMap<>(metadata);
        updated.put(key, value);
        return new TenantContext(tenantId, workspaceId, Map.copyOf(updated));
    }

    /**
     * DC-17: Returns a new {@code TenantContext} with the given workspace ID.
     * Enables workspace-level scoping for multi-workspace tenants.
     *
     * @param workspaceId the workspace ID (null to clear workspace)
     * @return new TenantContext with the updated workspace ID
     */
    public TenantContext withWorkspace(String workspaceId) {
        return new TenantContext(tenantId, Optional.ofNullable(workspaceId), metadata);
    }
}
