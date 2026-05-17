package com.ghatana.yappc.domain.artifact;

import java.util.Map;
import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Typed DTO for unresolved graph edges emitted during extraction
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 */
public record UnresolvedGraphEdgeDto(
    String id,
    String sourceNodeId,
    String targetRef,
    String relationshipType,
    String targetKindHint,
    SourceLocation sourceLocation,
    Double confidence,
    Map<String, Object> metadata,
    String tenantId,
    String projectId,
    String workspaceId
) {
    public UnresolvedGraphEdgeDto {
        Objects.requireNonNull(sourceNodeId, "sourceNodeId must not be null");
        Objects.requireNonNull(targetRef, "targetRef must not be null");
        Objects.requireNonNull(relationshipType, "relationshipType must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public record SourceLocation(
        String filePath,
        int startLine,
        int startColumn,
        int endLine,
        int endColumn
    ) {
        public SourceLocation {
            Objects.requireNonNull(filePath, "filePath must not be null");
        }
    }
}
