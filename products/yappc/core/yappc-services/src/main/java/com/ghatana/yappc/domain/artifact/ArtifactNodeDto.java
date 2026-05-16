package com.ghatana.yappc.domain.artifact;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Lightweight DTO for artifact node ingestion with full fidelity
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 * 
 * P0-5: Extended with source location, extractor metadata, confidence, provenance,
 * privacy flags, residual fragments, and symbol references to match TypeScript GraphNode.
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
     * P0-5: Source location with line/column ranges
     */
    Map<String, Object> sourceLocation,
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
}
