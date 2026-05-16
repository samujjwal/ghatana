package com.ghatana.yappc.domain.artifact;

import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Request payload for running graph analysis algorithms on an artifact graph
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 */
public record ArtifactGraphAnalysisRequest(
    String projectId,
    String tenantId,
    List<String> algorithmTypes,
    List<String> nodeIds
) {
}
