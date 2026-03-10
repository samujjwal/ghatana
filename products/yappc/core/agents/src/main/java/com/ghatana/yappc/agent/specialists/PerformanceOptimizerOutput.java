package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from PerformanceOptimizer agent.
 *
 * @doc.type record
 * @doc.purpose Expert performance optimizer for profiling and optimization recommendations output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PerformanceOptimizerOutput(@NotNull String optimizationId, @NotNull List<String> recommendations, @NotNull Map<String, Object> projectedImpact, @NotNull Map<String, Object> metadata) {
  public PerformanceOptimizerOutput {
    if (optimizationId == null || optimizationId.isEmpty()) {
      throw new IllegalArgumentException("optimizationId cannot be null or empty");
    }
    if (recommendations == null) {
      recommendations = List.of();
    }
    if (projectedImpact == null) {
      projectedImpact = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
