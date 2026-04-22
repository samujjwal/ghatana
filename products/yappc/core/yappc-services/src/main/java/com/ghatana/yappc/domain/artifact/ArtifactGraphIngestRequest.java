package com.ghatana.yappc.domain.artifact;

import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Request payload for ingesting an artifact graph from the TypeScript scanner
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 */
public record ArtifactGraphIngestRequest(
    String productId,
    String tenantId,
    List<ArtifactNodeDto> nodes,
    List<ArtifactEdgeDto> edges
) {
}
