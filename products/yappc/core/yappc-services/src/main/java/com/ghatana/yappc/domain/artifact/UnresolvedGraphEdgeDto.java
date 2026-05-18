package com.ghatana.yappc.domain.artifact;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Typed DTO for unresolved graph edges emitted during extraction
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 *
 * P1: Uses shared SourceLocationDto and validates confidence range and relationship types.
 * P1: Added adapter methods for proto-generated classes compatibility.
 */
public record UnresolvedGraphEdgeDto(
    String id,
    String sourceNodeId,
    String targetRef,
    String relationshipType,
    String targetKindHint,
    SourceLocationDto sourceLocation,
    Double confidence,
    Map<String, Object> metadata,
    String tenantId,
    String projectId,
    String workspaceId
) {
    // P1: Allowed relationship types matching the canonical schema
    private static final java.util.Set<String> ALLOWED_RELATIONSHIP_TYPES = java.util.Set.of(
        "contains", "imports", "exports", "calls", "uses", "renders", "routes-to",
        "extends", "implements", "styles", "story-for", "references", "depends-on",
        "instantiates", "overrides", "annotates", "decorates", "wraps"
    );

    public UnresolvedGraphEdgeDto {
        Objects.requireNonNull(sourceNodeId, "sourceNodeId must not be null");
        Objects.requireNonNull(targetRef, "targetRef must not be null");
        Objects.requireNonNull(relationshipType, "relationshipType must not be null");
        
        // P1: Validate relationship type is in the allowed set
        if (!ALLOWED_RELATIONSHIP_TYPES.contains(relationshipType)) {
            throw new IllegalArgumentException(
                "relationshipType must be one of: " + ALLOWED_RELATIONSHIP_TYPES + ", got: " + relationshipType);
        }
        
        // P1: Validate confidence is in range [0.0, 1.0]
        if (confidence != null && (confidence < 0.0 || confidence > 1.0)) {
            throw new IllegalArgumentException("confidence must be in range [0.0, 1.0], got: " + confidence);
        }
        
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * P1: Adapter method to convert from proto-generated UnresolvedGraphEdge to domain DTO.
     * Provides compatibility layer between proto contract and validated domain model.
     */
    public static UnresolvedGraphEdgeDto fromProto(com.ghatana.yappc.artifact.grpc.UnresolvedGraphEdge proto) {
        return new UnresolvedGraphEdgeDto(
            proto.getEdgeId(),
            proto.getSourceNodeId(),
            proto.getTargetRef(),
            proto.getRelationshipType(),
            proto.getTargetKindHint(),
            null,  // sourceLocation not yet supported in proto
            proto.getConfidence(),
            Map.copyOf(proto.getMetadataMap()),
            proto.getTenantId(),
            proto.getProjectId(),
            proto.getWorkspaceId()
        );
    }

    /**
     * P1: Adapter method to convert domain DTO to proto-generated UnresolvedGraphEdge.
     * Provides compatibility layer between validated domain model and proto contract.
     */
    public com.ghatana.yappc.artifact.grpc.UnresolvedGraphEdge toProto() {
        com.ghatana.yappc.artifact.grpc.UnresolvedGraphEdge.Builder builder = com.ghatana.yappc.artifact.grpc.UnresolvedGraphEdge.newBuilder()
            .setEdgeId(id)
            .setSourceNodeId(sourceNodeId)
            .setTargetRef(targetRef)
            .setRelationshipType(relationshipType)
            .setTargetKindHint(targetKindHint != null ? targetKindHint : "")
            .setConfidence(confidence != null ? confidence : 0.0)
            .putAllMetadata(convertMetadataToStringMap(metadata))
            .setTenantId(tenantId)
            .setProjectId(projectId)
            .setWorkspaceId(workspaceId);

        // sourceLocation not yet supported in proto

        return builder.build();
    }

    /**
     * P1: Helper method to convert Map<String,Object> to Map<String,String> for proto compatibility.
     */
    private static Map<String, String> convertMetadataToStringMap(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            result.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return result;
    }
}
