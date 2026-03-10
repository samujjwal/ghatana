package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for DebugOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Orchestrates debugging workflows using specialized debug micro-agents input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DebugOrchestratorInput(@NotNull String incidentId, @NotNull String errorDescription, @NotNull Map<String, Object> diagnosticContext) {
  public DebugOrchestratorInput {
    if (incidentId == null || incidentId.isEmpty()) {
      throw new IllegalArgumentException("incidentId cannot be null or empty");
    }
    if (errorDescription == null || errorDescription.isEmpty()) {
      throw new IllegalArgumentException("errorDescription cannot be null or empty");
    }
    if (diagnosticContext == null) {
      diagnosticContext = Map.of();
    }
  }
}
