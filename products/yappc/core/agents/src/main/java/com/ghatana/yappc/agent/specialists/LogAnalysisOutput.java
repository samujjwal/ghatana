package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from LogAnalysis agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that analyzes log files for error patterns and anomalies output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record LogAnalysisOutput(@NotNull String analysisId, @NotNull List<String> patterns, @NotNull List<String> anomalies, @NotNull Map<String, Object> metadata) {
  public LogAnalysisOutput {
    if (analysisId == null || analysisId.isEmpty()) {
      throw new IllegalArgumentException("analysisId cannot be null or empty");
    }
    if (patterns == null) {
      patterns = List.of();
    }
    if (anomalies == null) {
      anomalies = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
