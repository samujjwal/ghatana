package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for CanaryAnalysis agent.
 *
 * @doc.type record
 * @doc.purpose Release governance agent that analyzes canary deployment metrics input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CanaryAnalysisInput(@NotNull String canaryId, @NotNull String baselineId, @NotNull Map<String, Object> metrics) {
  public CanaryAnalysisInput {
    if (canaryId == null || canaryId.isEmpty()) {
      throw new IllegalArgumentException("canaryId cannot be null or empty");
    }
    if (baselineId == null || baselineId.isEmpty()) {
      throw new IllegalArgumentException("baselineId cannot be null or empty");
    }
    if (metrics == null) {
      metrics = Map.of();
    }
  }
}
