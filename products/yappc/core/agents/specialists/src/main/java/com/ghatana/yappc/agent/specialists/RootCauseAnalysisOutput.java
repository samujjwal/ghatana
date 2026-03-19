package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from RootCauseAnalysis agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that performs systematic root cause analysis output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RootCauseAnalysisOutput(@NotNull String analysisId, @NotNull String rootCause, @NotNull List<String> contributingFactors, @NotNull String confidence, @NotNull Map<String, Object> metadata) {
  public RootCauseAnalysisOutput {
    if (analysisId == null || analysisId.isEmpty()) {
      throw new IllegalArgumentException("analysisId cannot be null or empty");
    }
    if (rootCause == null || rootCause.isEmpty()) {
      throw new IllegalArgumentException("rootCause cannot be null or empty");
    }
    if (contributingFactors == null) {
      contributingFactors = List.of();
    }
    if (confidence == null || confidence.isEmpty()) {
      throw new IllegalArgumentException("confidence cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
