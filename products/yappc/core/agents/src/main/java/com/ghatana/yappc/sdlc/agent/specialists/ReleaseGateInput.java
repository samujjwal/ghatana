package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ReleaseGate agent.
 *
 * @doc.type record
 * @doc.purpose Release governance agent that enforces release quality gates input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReleaseGateInput(@NotNull String releaseId, @NotNull Map<String, Object> qualityMetrics, @NotNull Map<String, Object> thresholds) {
  public ReleaseGateInput {
    if (releaseId == null || releaseId.isEmpty()) {
      throw new IllegalArgumentException("releaseId cannot be null or empty");
    }
    if (qualityMetrics == null) {
      qualityMetrics = Map.of();
    }
    if (thresholds == null) {
      thresholds = Map.of();
    }
  }
}
