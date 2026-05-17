package com.ghatana.yappc.domain.artifact;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Typed DTO for unresolved edge resolution outcomes
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 */
public record EdgeResolutionRecordDto(
    String id,
    String unresolvedEdgeId,
    String status,
    String resolvedTargetId,
    List<String> candidateIds,
    boolean reviewRequired,
    String resolutionMethod,
    Map<String, Object> metadata,
    String tenantId,
    String projectId,
    String workspaceId
) {
    public EdgeResolutionRecordDto {
        Objects.requireNonNull(unresolvedEdgeId, "unresolvedEdgeId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        candidateIds = candidateIds == null ? List.of() : List.copyOf(candidateIds);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
