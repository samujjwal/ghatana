package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for PerformanceOptimizer agent.
 *
 * @doc.type record
 * @doc.purpose Expert performance optimizer for profiling and optimization recommendations input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PerformanceOptimizerInput(@NotNull String serviceId, @NotNull Map<String, Object> performanceMetrics, @NotNull String targetSLA) {
  public PerformanceOptimizerInput {
    if (serviceId == null || serviceId.isEmpty()) {
      throw new IllegalArgumentException("serviceId cannot be null or empty");
    }
    if (performanceMetrics == null) {
      performanceMetrics = Map.of();
    }
    if (targetSLA == null || targetSLA.isEmpty()) {
      throw new IllegalArgumentException("targetSLA cannot be null or empty");
    }
  }
}
