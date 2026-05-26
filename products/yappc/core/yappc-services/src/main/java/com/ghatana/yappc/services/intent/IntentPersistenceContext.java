package com.ghatana.yappc.services.intent;

import java.util.Map;
import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Tenant-scoped context required to persist a versioned YAPPC intent
 * @doc.layer service
 * @doc.pattern Value Object
 */
public record IntentPersistenceContext(
        String tenantId,
        String workspaceId,
        String projectId,
        String actorId,
        String auditEventId,
        List<String> evidenceIds,
        Map<String, Object> metadata
) {
    public IntentPersistenceContext {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
