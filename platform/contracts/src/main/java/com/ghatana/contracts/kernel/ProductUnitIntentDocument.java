package com.ghatana.contracts.kernel;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Canonical Java DTO for ProductUnitIntent export serialization
 * @doc.layer platform
 * @doc.pattern Contract
 */
public record ProductUnitIntentDocument(
        String schemaVersion,
        String intentId,
        String intentType,
        ProductUnitScopeDocument scope,
        ProducerDocument producer,
        TargetProvidersDocument target,
        ProductUnitDraftDocument productUnit,
        RequestedLifecycleDocument requestedLifecycle
) {

    public record ProductUnitScopeDocument(String tenantId, String workspaceId, String projectId) {
    }

    public record ProducerDocument(String id, String type, String name, String correlationId) {
    }

    public record TargetProvidersDocument(String registryProvider, String sourceProvider) {
    }

    public record ProductUnitDraftDocument(
            String id,
            String name,
            String kind,
            List<ProductUnitSurfaceDocument> surfaces,
            String lifecycleProfile,
            Map<String, Object> metadata
    ) {
    }

    public record ProductUnitSurfaceDocument(String id, String type, String implementationStatus) {
    }

    public record RequestedLifecycleDocument(String profile, boolean enableExecution) {
    }
}