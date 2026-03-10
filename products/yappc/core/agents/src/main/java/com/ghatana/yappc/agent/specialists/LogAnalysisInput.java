package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for LogAnalysis agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that analyzes log files for error patterns and anomalies input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record LogAnalysisInput(@NotNull String logSource, @NotNull String timeRange, @NotNull Map<String, Object> filters) {
  public LogAnalysisInput {
    if (logSource == null || logSource.isEmpty()) {
      throw new IllegalArgumentException("logSource cannot be null or empty");
    }
    if (timeRange == null || timeRange.isEmpty()) {
      throw new IllegalArgumentException("timeRange cannot be null or empty");
    }
    if (filters == null) {
      filters = Map.of();
    }
  }
}
