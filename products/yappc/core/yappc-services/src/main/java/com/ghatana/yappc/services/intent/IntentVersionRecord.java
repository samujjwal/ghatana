package com.ghatana.yappc.services.intent;

import com.ghatana.yappc.domain.intent.IntentSpec;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Versioned durable state for a captured YAPPC intent
 * @doc.layer service
 * @doc.pattern DTO
 */
public record IntentVersionRecord(
        String recordId,
        String tenantId,
        String workspaceId,
        String projectId,
        String intentId,
        int version,
        IntentSpec spec,
        String createdBy,
        Instant createdAt,
        String auditEventId,
        List<String> evidenceIds,
        Map<String, Object> metadata
) {
    public IntentVersionRecord {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
