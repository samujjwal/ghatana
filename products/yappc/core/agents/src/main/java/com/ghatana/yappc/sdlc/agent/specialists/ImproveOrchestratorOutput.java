package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ImproveOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Orchestrates continuous improvement across codebase and processes output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ImproveOrchestratorOutput(@NotNull String improvementId, @NotNull List<String> actionItems, @NotNull Map<String, Object> impact, @NotNull Map<String, Object> metadata) {
  public ImproveOrchestratorOutput {
    if (improvementId == null || improvementId.isEmpty()) {
      throw new IllegalArgumentException("improvementId cannot be null or empty");
    }
    if (actionItems == null) {
      actionItems = List.of();
    }
    if (impact == null) {
      impact = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
