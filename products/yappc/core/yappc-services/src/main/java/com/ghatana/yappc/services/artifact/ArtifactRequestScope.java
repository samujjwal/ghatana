package com.ghatana.yappc.services.artifact;

import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Explicit server-derived scope for artifact graph service operations
 * @doc.layer service
 * @doc.pattern DataTransferObject
 */
public record ArtifactRequestScope(
        String productId,
        String tenantId
) {
    public ArtifactRequestScope {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
    }
}