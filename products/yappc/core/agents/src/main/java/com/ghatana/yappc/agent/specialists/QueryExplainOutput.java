package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from QueryExplain agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that analyzes SQL query execution plans for optimization output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record QueryExplainOutput(@NotNull String analysisId, @NotNull List<String> bottlenecks, @NotNull List<String> optimizations, @NotNull Map<String, Object> metadata) {
  public QueryExplainOutput {
    if (analysisId == null || analysisId.isEmpty()) {
      throw new IllegalArgumentException("analysisId cannot be null or empty");
    }
    if (bottlenecks == null) {
      bottlenecks = List.of();
    }
    if (optimizations == null) {
      optimizations = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
