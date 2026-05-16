package com.ghatana.yappc.domain.artifact;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Request payload for ingesting an artifact graph with full fidelity
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 * 
 * P0-4: Extended with snapshot metadata, unresolved edges, resolution records, and residual islands
 * to preserve complete graph lifecycle information across the backend API boundary.
 */
public record ArtifactGraphIngestRequest(
    String productId,
    String tenantId,
    List<ArtifactNodeDto> nodes,
    List<ArtifactEdgeDto> edges,
    /**
     * P0-4: Stable snapshot reference identifying the source repository commit
     */
    String snapshotRef,
    /**
     * P0-4: Unique snapshot identifier (e.g., commit SHA or archive checksum)
     */
    String snapshotId,
    /**
     * P0-4: Version identifier for this graph snapshot
     */
    String versionId,
    /**
     * P0-4: Content checksum for change detection and deduplication
     */
    String contentChecksum,
    /**
     * P0-4: Unresolved edges that could not be resolved during extraction
     */
    List<Map<String, Object>> unresolvedEdges,
    /**
     * P0-4: Records of edge resolution attempts and outcomes
     */
    List<Map<String, Object>> edgeResolutionRecords,
    /**
     * P0-4: IDs of residual islands that could not be modeled
     */
    List<String> residualIslandIds
) {
}
