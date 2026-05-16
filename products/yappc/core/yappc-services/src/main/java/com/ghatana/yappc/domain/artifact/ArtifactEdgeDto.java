package com.ghatana.yappc.domain.artifact;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Lightweight DTO for artifact relationship edge ingestion with full fidelity
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 * 
 * P0-6: Extended with edge ID, confidence, bidirectional flag, metadata, and snapshot metadata.
 * Enforces source/target as resolved node IDs (never component name strings).
 */
public record ArtifactEdgeDto(
    /**
     * P0-6: Unique edge identifier (deterministic URN or UUID)
     */
    String edgeId,
    /**
     * P0-6: Resolved source node ID - must reference a valid node in the graph
     */
    String sourceNodeId,
    /**
     * P0-6: Resolved target node ID - must reference a valid node in the graph
     */
    String targetNodeId,
    String relationshipType,
    Map<String, Object> properties,
    /**
     * P0-6: Confidence score (0.0 to 1.0) in the edge extraction
     */
    Double confidence,
    /**
     * P0-6: Whether the edge relationship is bidirectional
     */
    Boolean bidirectional,
    /**
     * P0-6: Additional edge metadata
     */
    Map<String, Object> metadata,
    /**
     * P0-6: Snapshot identifier this edge belongs to
     */
    String snapshotId,
    /**
     * P0-6: Version identifier for this edge
     */
    String versionId
) {
}
