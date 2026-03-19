package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ComplianceGapAnalysis agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that performs gap analysis against compliance frameworks output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ComplianceGapAnalysisOutput(@NotNull String analysisId, @NotNull List<String> gaps, @NotNull String complianceScore, @NotNull Map<String, Object> metadata) {
  public ComplianceGapAnalysisOutput {
    if (analysisId == null || analysisId.isEmpty()) {
      throw new IllegalArgumentException("analysisId cannot be null or empty");
    }
    if (gaps == null) {
      gaps = List.of();
    }
    if (complianceScore == null || complianceScore.isEmpty()) {
      throw new IllegalArgumentException("complianceScore cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
