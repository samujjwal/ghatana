package com.ghatana.yappc.services.shape;

import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.shape.SystemModel;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Versioned durable state for a YAPPC shape artifact
 * @doc.layer service
 * @doc.pattern DTO
 */
public record ShapeVersionRecord(
        String recordId,
        String tenantId,
        String workspaceId,
        String projectId,
        String shapeId,
        int version,
        ShapeSpec shape,
        SystemModel systemModel,
        String createdBy,
        Instant createdAt,
        String sourceIntentId,
        List<String> intentEvidenceIds,
        Map<String, Object> metadata
) {
    public ShapeVersionRecord {
        intentEvidenceIds = intentEvidenceIds == null ? List.of() : List.copyOf(intentEvidenceIds);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
