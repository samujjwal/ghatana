package com.ghatana.yappc.domain.artifact;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Typed DTO for a residual island - a code fragment that could not be modeled as an artifact node
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 *
 * P0: Replaces ID-only List&lt;String&gt; ingestion. Every residual island from the TS worker must
 * carry its full payload so persistence can record originalSource, sourceLocation, sourceSpan, checksum,
 * rawFragmentRef, risk, and reviewRequired without synthesizing placeholder values.
 *
 * Fields mirror the canonical proto {@code ResidualIsland} message in {@code artifact_compiler.proto}.
 * P1: Added adapter methods for proto-generated classes compatibility.
 */
public record ResidualIslandDto(
    /**
     * Stable, deterministic island ID (SHA-256 of source span + snapshot ID).
     */
    String id,

    /**
     * Classifier for the island type (e.g. "imperative_logic", "css_module", "raw_query").
     */
    String islandType,

    /**
     * Human-readable summary of the unmodeled code.
     */
    String summary,

    /**
     * P0: Original source code fragment for complete round-trip fidelity.
     * Required for compile-back to preserve exact source when regenerating files.
     */
    String originalSource,

    /**
        * P1: Structured source location using shared SourceLocationDto.
        * This is the canonical location representation for persistence and validation.
        */
        SourceLocationDto sourceLocation,

        /**
     * Source span as "file:startLine:startCol-endLine:endCol".
     * Required for round-trip fidelity; never synthesized.
     */
    String sourceSpan,

    /**
     * SHA-256 checksum of the raw fragment content.
     */
    String checksum,

    /**
     * Reference (path or blob hash) to the raw fragment in the content store.
     */
    String rawFragmentRef,

    /**
     * Machine-readable reason the fragment could not be modeled.
     */
    String reason,

    /**
     * Extraction confidence in [0, 1].
     */
    Double confidence,

    /**
     * Whether a human must review this island before compile-back is safe.
     */
    Boolean reviewRequired,

    /**
     * Risk score in [0, 1]; drives review prioritisation.
     */
    Double riskScore,

    /**
     * Additional key-value metadata from the extractor.
     */
    Map<String, String> metadata,

    /**
     * Number of source files contributing to this island.
     */
    Integer fileCount,

    String tenantId,
    String projectId,
    String workspaceId,
    String snapshotId
) {
    /**
     * P0: Validate that originalSource and checksum are required for complete round-trip fidelity.
     */
    public ResidualIslandDto {
        java.util.Objects.requireNonNull(originalSource, "originalSource must not be null");
        java.util.Objects.requireNonNull(checksum, "checksum must not be null");
        if (originalSource.isBlank()) {
            throw new IllegalArgumentException("originalSource must not be blank");
        }
        if (checksum.isBlank()) {
            throw new IllegalArgumentException("checksum must not be blank");
        }
    }

    /**
     * P1: Compact canonical constructor requiring originalSource and checksum.
     * This is the recommended constructor for creating ResidualIslandDto instances.
     */
    public ResidualIslandDto(
        String id,
        String islandType,
        String summary,
        String originalSource,
        SourceLocationDto sourceLocation,
        String checksum,
        String rawFragmentRef,
        String reason,
        Double confidence,
        Boolean reviewRequired,
        Double riskScore,
        Map<String, String> metadata,
        Integer fileCount,
        String tenantId,
        String projectId,
        String workspaceId,
        String snapshotId
    ) {
        this(
            id,
            islandType,
            summary,
            originalSource,
            sourceLocation,
            null,  // sourceSpan is optional
            checksum,
            rawFragmentRef,
            reason,
            confidence,
            reviewRequired,
            riskScore,
            metadata,
            fileCount,
            tenantId,
            projectId,
            workspaceId,
            snapshotId
        );
    }

    /**
     * P1: Adapter method to convert from proto-generated ResidualIsland to domain DTO.
     * Provides compatibility layer between proto contract and validated domain model.
     */
    public static ResidualIslandDto fromProto(com.ghatana.yappc.artifact.grpc.ResidualIsland proto) {
        return new ResidualIslandDto(
            proto.getId(),
            proto.getIslandType(),
            proto.getSummary(),
            proto.getOriginalSource(),
            proto.hasSourceLocation() ? SourceLocationDto.fromProto(proto.getSourceLocation()) : null,
            proto.getSourceSpan(),
            proto.getChecksum(),
            proto.getRawFragmentRef(),
            proto.getReason(),
            proto.getConfidence(),
            proto.getReviewRequired(),
            proto.getRiskScore(),
            Map.copyOf(proto.getMetadataMap()),
            proto.getFileCount(),
            proto.getTenantId(),
            proto.getProjectId(),
            proto.getWorkspaceId(),
            proto.getSnapshotId()
        );
    }

    /**
     * P1: Adapter method to convert domain DTO to proto-generated ResidualIsland.
     * Provides compatibility layer between validated domain model and proto contract.
     */
    public com.ghatana.yappc.artifact.grpc.ResidualIsland toProto() {
        com.ghatana.yappc.artifact.grpc.ResidualIsland.Builder builder = com.ghatana.yappc.artifact.grpc.ResidualIsland.newBuilder()
            .setId(id)
            .setIslandType(islandType)
            .setSummary(summary)
            .setOriginalSource(originalSource)
            .setChecksum(checksum)
            .setRawFragmentRef(rawFragmentRef)
            .setReason(reason)
            .setConfidence(confidence != null ? confidence : 0.0)
            .setReviewRequired(reviewRequired != null ? reviewRequired : false)
            .setRiskScore(riskScore)
            .putAllMetadata(metadata != null ? metadata : Map.of())
            .setFileCount(fileCount != null ? fileCount : 1)
            .setTenantId(tenantId)
            .setProjectId(projectId)
            .setWorkspaceId(workspaceId)
            .setSnapshotId(snapshotId);

        if (sourceLocation != null) {
            builder.setSourceLocation(sourceLocation.toProto());
        }
        if (sourceSpan != null) {
            builder.setSourceSpan(sourceSpan);
        }

        return builder.build();
    }
}
