package com.ghatana.yappc.services.shape;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Tenant-scoped context required to persist shape artifacts
 * @doc.layer service
 * @doc.pattern Value Object
 */
public record ShapePersistenceContext(
        String tenantId,
        String workspaceId,
        String projectId,
        String actorId,
        String sourceIntentId,
        List<String> intentEvidenceIds,
        Map<String, Object> metadata
) {
    public ShapePersistenceContext {
        intentEvidenceIds = intentEvidenceIds == null ? List.of() : List.copyOf(intentEvidenceIds);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
