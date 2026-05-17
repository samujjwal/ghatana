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
    public static ArtifactGraphIngestRequest fromLegacyMaps(
        String projectId,
        String tenantId,
        List<ArtifactNodeDto> nodes,
        List<ArtifactEdgeDto> edges,
        String snapshotRef,
        String snapshotId,
        String versionId,
        String contentChecksum,
        List<Map<String, Object>> unresolvedEdges,
        List<Map<String, Object>> edgeResolutionRecords,
        List<ResidualIslandDto> residualIslands
    ) {
        return new ArtifactGraphIngestRequest(
            projectId,
            tenantId,
            nodes,
            edges,
            snapshotRef,
            snapshotId,
            versionId,
            contentChecksum,
            unresolvedEdges == null ? List.of() : unresolvedEdges.stream().map(ArtifactGraphIngestRequest::toUnresolvedEdge).toList(),
            edgeResolutionRecords == null ? List.of() : edgeResolutionRecords.stream().map(ArtifactGraphIngestRequest::toResolutionRecord).toList(),
            residualIslands
        );
    }

    private static UnresolvedGraphEdgeDto toUnresolvedEdge(Map<String, Object> edge) {
        String sourceNodeId = stringValue(edge, "sourceNodeId", null);
        String targetRef = stringValue(edge, "targetRef", null);
        String relationshipType = stringValue(edge, "relationshipType", null);
        if (sourceNodeId == null || targetRef == null || relationshipType == null) {
            throw new IllegalArgumentException("Invalid unresolved edge payload: sourceNodeId, targetRef, and relationshipType are required");
        }
        return new UnresolvedGraphEdgeDto(
            stringValue(edge, "id", UUID.randomUUID().toString()),
            sourceNodeId,
            targetRef,
            relationshipType,
            stringValue(edge, "targetKindHint", null),
            null,
            doubleValue(edge.get("confidence")),
            mapValue(edge.get("metadata")),
            stringValue(edge, "tenantId", null),
            stringValue(edge, "projectId", null),
            stringValue(edge, "workspaceId", null)
        );
    }

    private static EdgeResolutionRecordDto toResolutionRecord(Map<String, Object> record) {
        String unresolvedEdgeId = stringValue(record, "unresolvedEdgeId", null);
        String status = stringValue(record, "status", null);
        if (unresolvedEdgeId == null || status == null) {
            throw new IllegalArgumentException("Invalid edge resolution record payload: unresolvedEdgeId and status are required");
        }
        return new EdgeResolutionRecordDto(
            stringValue(record, "id", UUID.randomUUID().toString()),
            unresolvedEdgeId,
            status,
            stringValue(record, "resolvedTargetId", null),
            listOfStrings(record.get("candidateIds")),
            Boolean.TRUE.equals(record.get("reviewRequired")),
            stringValue(record, "resolutionMethod", null),
            mapValue(record.get("metadata")),
            stringValue(record, "tenantId", null),
            stringValue(record, "projectId", null),
            stringValue(record, "workspaceId", null)
        );
    }

    private static String stringValue(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return fallback;
    }

    private static Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> raw) {
            return (Map<String, Object>) raw;
        }
        return Map.of();
    }

    private static List<String> listOfStrings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
    }
}
