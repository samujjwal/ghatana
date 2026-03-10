package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ComplianceGapAnalysis agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that performs gap analysis against compliance frameworks input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ComplianceGapAnalysisInput(@NotNull String frameworkId, @NotNull List<String> controlIds, @NotNull Map<String, Object> currentState) {
  public ComplianceGapAnalysisInput {
    if (frameworkId == null || frameworkId.isEmpty()) {
      throw new IllegalArgumentException("frameworkId cannot be null or empty");
    }
    if (controlIds == null) {
      controlIds = List.of();
    }
    if (currentState == null) {
      currentState = Map.of();
    }
  }
}
