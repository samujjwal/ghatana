package com.ghatana.yappc.domain.artifact;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Lightweight DTO for artifact relationship edge ingestion
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 */
public record ArtifactEdgeDto(
    String sourceNodeId,
    String targetNodeId,
    String relationshipType,
    Map<String, Object> properties
) {
}
