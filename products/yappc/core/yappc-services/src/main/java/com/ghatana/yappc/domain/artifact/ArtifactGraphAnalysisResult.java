package com.ghatana.yappc.domain.artifact;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Results from running graph analysis algorithms
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 */
public record ArtifactGraphAnalysisResult(
    String algorithm,
    Map<String, Double> centralityScores,
    List<List<String>> cycles,
    List<List<String>> communities,
    List<String> topologicalOrder,
    Map<String, Object> metadata
) {
}
