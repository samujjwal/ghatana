package com.ghatana.yappc.domain.artifact;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @doc.type record
 * @doc.purpose Request payload for ingesting an artifact graph with full fidelity
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 *
 * P0: Extended with snapshot metadata, unresolved edges, resolution records, and full residual island payloads.
 * Residual islands carry their complete payload (sourceSpan, checksum, rawFragmentRef, risk, reviewRequired)
 * rather than IDs only, so persistence never synthesises placeholder values.
 */
public record ArtifactGraphIngestRequest(
    String projectId,
    String tenantId,
    List<ArtifactNodeDto> nodes,
    List<ArtifactEdgeDto> edges,
    /**
     * Stable snapshot reference identifying the source repository commit.
     */
    String snapshotRef,
    /**
     * Unique snapshot identifier (e.g., commit SHA or archive checksum).
     */
    String snapshotId,
    /**
     * Version identifier for this graph snapshot.
     */
    String versionId,
    /**
     * Content checksum for change detection and deduplication.
     */
    String contentChecksum,
    /**
     * Unresolved edges that could not be resolved during extraction.
     */
    List<UnresolvedGraphEdgeDto> unresolvedEdges,
    /**
     * Records of edge resolution attempts and outcomes.
     */
    List<EdgeResolutionRecordDto> edgeResolutionRecords,
    /**
     * Full residual island payloads from the TS extractor worker.
     * Each entry carries sourceSpan, checksum, rawFragmentRef, riskScore, and reviewRequired.
     * Never null — use an empty list when no residuals are present.
     */
    List<ResidualIslandDto> residualIslands
) {
    // Removed fromLegacyMaps method - legacy migration helper no longer needed
    // All callers should use the canonical constructor with typed DTOs
}
