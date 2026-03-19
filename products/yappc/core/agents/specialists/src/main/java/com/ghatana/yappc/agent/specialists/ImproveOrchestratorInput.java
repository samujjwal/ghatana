package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ImproveOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Orchestrates continuous improvement across codebase and processes input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ImproveOrchestratorInput(@NotNull String projectId, @NotNull String improvementArea, @NotNull Map<String, Object> metrics) {
  public ImproveOrchestratorInput {
    if (projectId == null || projectId.isEmpty()) {
      throw new IllegalArgumentException("projectId cannot be null or empty");
    }
    if (improvementArea == null || improvementArea.isEmpty()) {
      throw new IllegalArgumentException("improvementArea cannot be null or empty");
    }
    if (metrics == null) {
      metrics = Map.of();
    }
  }
}
