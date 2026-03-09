package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from review specialist.
 *
 * @doc.type record
 * @doc.purpose Code review output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReviewOutput(
    @NotNull String reviewId,
    @NotNull boolean approved,
    @NotNull List<String> findings,
    @NotNull Map<String, Integer> qualityMetrics,
    @NotNull Map<String, Object> metadata) {

  public ReviewOutput {
    if (reviewId == null || reviewId.isEmpty()) {
      throw new IllegalArgumentException("reviewId cannot be null or empty");
    }
    if (findings == null) {
      findings = List.of();
    }
    if (qualityMetrics == null) {
      qualityMetrics = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
