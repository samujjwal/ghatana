package com.ghatana.yappc.domain.artifact;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Request payload for three-way semantic merge of artifact models
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 */
public record ArtifactGraphMergeRequest(
    String productId,
    String tenantId,
    Map<String, Object> baseModel,
    Map<String, Object> leftModel,
    Map<String, Object> rightModel,
    String resolutionStrategy
) {
}
