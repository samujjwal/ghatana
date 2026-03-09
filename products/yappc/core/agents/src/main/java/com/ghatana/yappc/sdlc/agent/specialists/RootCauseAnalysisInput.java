package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for RootCauseAnalysis agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that performs systematic root cause analysis input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RootCauseAnalysisInput(@NotNull String incidentId, @NotNull List<String> symptoms, @NotNull Map<String, Object> diagnosticData) {
  public RootCauseAnalysisInput {
    if (incidentId == null || incidentId.isEmpty()) {
      throw new IllegalArgumentException("incidentId cannot be null or empty");
    }
    if (symptoms == null) {
      symptoms = List.of();
    }
    if (diagnosticData == null) {
      diagnosticData = Map.of();
    }
  }
}
