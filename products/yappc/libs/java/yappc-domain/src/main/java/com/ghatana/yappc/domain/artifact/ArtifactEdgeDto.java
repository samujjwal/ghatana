package com.ghatana.yappc.domain.artifact;

import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Data transfer object for artifact edges from the TypeScript artifact compiler scanner.
 * @doc.layer domain
 * @doc.pattern DTO
 */
public record ArtifactEdgeDto(
    String sourceNodeId,
    String targetNodeId,
    String relationshipType,
    Map<String, Object> properties
) {
    public ArtifactEdgeDto {
        sourceNodeId = sourceNodeId != null ? sourceNodeId : "";
        targetNodeId = targetNodeId != null ? targetNodeId : "";
        relationshipType = relationshipType != null ? relationshipType : "uses";
        properties = properties != null ? Map.copyOf(properties) : Map.of();
    }
}
