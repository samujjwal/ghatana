package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from CanaryAnalysis agent.
 *
 * @doc.type record
 * @doc.purpose Release governance agent that analyzes canary deployment metrics output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CanaryAnalysisOutput(@NotNull String analysisId, @NotNull String verdict, double confidenceScore, @NotNull Map<String, Object> comparison, @NotNull Map<String, Object> metadata) {
  public CanaryAnalysisOutput {
    if (analysisId == null || analysisId.isEmpty()) {
      throw new IllegalArgumentException("analysisId cannot be null or empty");
    }
    if (verdict == null || verdict.isEmpty()) {
      throw new IllegalArgumentException("verdict cannot be null or empty");
    }
    if (comparison == null) {
      comparison = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
