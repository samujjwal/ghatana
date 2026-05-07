package com.ghatana.datacloud.spi;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Tenant context for multi-tenant storage operations.
 *
 * @doc.type record
 * @doc.purpose Multi-tenant context for storage operations
 * @doc.layer spi
 * @doc.pattern Value Object
 * @since 1.0.0
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

    /**
     * Create a simple tenant context with just tenant ID.
     */
    public static TenantContext of(String tenantId) {
        return new TenantContext(tenantId, Optional.empty(), Map.of());
    }

    /**
     * Create a tenant context with workspace.
     */
    public static TenantContext of(String tenantId, String workspaceId) {
        return new TenantContext(tenantId, Optional.ofNullable(workspaceId), Map.of());
    }

    /**
     * Create a tenant context with metadata.
     */
    public static TenantContext of(String tenantId, Map<String, String> metadata) {
        return new TenantContext(tenantId, Optional.empty(), metadata);
    }

    /**
     * Add metadata to this context.
     */
    public TenantContext withMetadata(String key, String value) {
        var newMetadata = new java.util.HashMap<>(metadata);
        newMetadata.put(key, value);
        return new TenantContext(tenantId, workspaceId, newMetadata);
    }

    /**
     * Add workspace to this context.
     */
    public TenantContext withWorkspace(String workspaceId) {
        return new TenantContext(tenantId, Optional.ofNullable(workspaceId), metadata);
    }
}
