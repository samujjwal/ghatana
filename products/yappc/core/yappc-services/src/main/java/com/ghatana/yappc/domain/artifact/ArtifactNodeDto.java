package com.ghatana.yappc.domain.artifact;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Lightweight DTO for artifact node ingestion with full fidelity
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 *
 * P0-5: Extended with source location, extractor metadata, confidence, provenance,
 * privacy flags, residual fragments, and symbol references to match TypeScript GraphNode.
 * P1: Added adapter methods for proto-generated classes compatibility.
 */
public record ArtifactNodeDto(
    String id,
    String type,
    String name,
    String filePath,
    String content,
    Map<String, Object> properties,
    List<String> tags,
    String tenantId,
    String projectId,
    /**
     * P0-5: Source location with line/column ranges using shared SourceLocationDto.
     */
    SourceLocationDto sourceLocation,
    /**
     * P0-5: Identifier of the extractor that produced this node
     */
    String extractorId,
    /**
     * P0-5: Version of the extractor that produced this node
     */
    String extractorVersion,
    /**
     * P0-5: Confidence score (0.0 to 1.0) in the extraction
     */
    Double confidence,
    /**
     * P0-5: Provenance of the node (exact, inferred, synthesized, manual, assumed)
     */
    String provenance,
    /**
     * P0-5: Privacy and security flags (e.g., PII, secrets, sensitive)
     */
    List<String> privacySecurityFlags,
    /**
     * P0-5: IDs of residual fragments that couldn't be modeled
     */
    List<String> residualFragmentIds,
    /**
     * P0-5: Stable source identity (deterministic URN)
     */
    String sourceRef,
    /**
     * P0-5: Qualified symbol reference (path#kind:name)
     */
    String symbolRef
) {
    /**
     * P1: Adapter method to convert from proto-generated ArtifactNode to domain DTO.
     * Provides compatibility layer between proto contract and validated domain model.
     */
    public static ArtifactNodeDto fromProto(com.ghatana.yappc.artifact.grpc.ArtifactNode proto) {
        return new ArtifactNodeDto(
            proto.getId(),
            proto.getType(),
            proto.getName(),
            proto.getFilePath(),
            proto.getContent(),
            Map.copyOf(proto.getPropertiesMap()),
            List.copyOf(proto.getTagsList()),
            proto.getTenantId(),
            proto.getProjectId(),
            proto.hasSourceLocation() ? SourceLocationDto.fromProto(proto.getSourceLocation()) : null,
            proto.getExtractorId(),
            proto.getExtractorVersion(),
            proto.getConfidence(),
            proto.getProvenance(),
            List.copyOf(proto.getSecurityFlagsList()),
            List.copyOf(proto.getResidualFragmentIdsList()),
            proto.hasSnapshotRef() ? proto.getSnapshotRef().toString() : null,
            null // symbolRef not in proto
        );
    }

    /**
     * P1: Adapter method to convert domain DTO to proto-generated ArtifactNode.
     * Provides compatibility layer between validated domain model and proto contract.
     */
    public com.ghatana.yappc.artifact.grpc.ArtifactNode toProto() {
        com.ghatana.yappc.artifact.grpc.ArtifactNode.Builder builder = com.ghatana.yappc.artifact.grpc.ArtifactNode.newBuilder()
            .setId(id)
            .setType(type)
            .setName(name)
            .setFilePath(filePath)
            .setContent(content)
            .putAllProperties(convertPropertiesToStringMap(properties))
            .addAllTags(tags)
            .setTenantId(tenantId)
            .setProjectId(projectId)
            .setExtractorId(extractorId)
            .setExtractorVersion(extractorVersion)
            .setConfidence(confidence != null ? confidence : 0.0)
            .setProvenance(provenance != null ? provenance : "exact")
            .addAllSecurityFlags(privacySecurityFlags != null ? privacySecurityFlags : List.of())
            .addAllResidualFragmentIds(residualFragmentIds != null ? residualFragmentIds : List.of());

        if (sourceLocation != null) {
            builder.setSourceLocation(sourceLocation.toProto());
        }

        return builder.build();
    }

    /**
     * P1: Helper method to convert Map<String,Object> to Map<String,String> for proto compatibility.
     */
    private static Map<String, String> convertPropertiesToStringMap(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            result.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return result;
    }
}
