package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for FullLifecycleOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Orchestrates the complete SDLC lifecycle from intake to production input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record FullLifecycleOrchestratorInput(@NotNull String projectId, @NotNull String lifecyclePhase, @NotNull Map<String, Object> phaseContext) {
  public FullLifecycleOrchestratorInput {
    if (projectId == null || projectId.isEmpty()) {
      throw new IllegalArgumentException("projectId cannot be null or empty");
    }
    if (lifecyclePhase == null || lifecyclePhase.isEmpty()) {
      throw new IllegalArgumentException("lifecyclePhase cannot be null or empty");
    }
    if (phaseContext == null) {
      phaseContext = Map.of();
    }
  }
}
