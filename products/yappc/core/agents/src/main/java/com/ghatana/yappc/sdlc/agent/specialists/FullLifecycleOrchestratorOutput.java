package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from FullLifecycleOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Orchestrates the complete SDLC lifecycle from intake to production output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record FullLifecycleOrchestratorOutput(@NotNull String orchestrationId, @NotNull String nextPhase, @NotNull List<String> tasks, @NotNull Map<String, Object> metadata) {
  public FullLifecycleOrchestratorOutput {
    if (orchestrationId == null || orchestrationId.isEmpty()) {
      throw new IllegalArgumentException("orchestrationId cannot be null or empty");
    }
    if (nextPhase == null || nextPhase.isEmpty()) {
      throw new IllegalArgumentException("nextPhase cannot be null or empty");
    }
    if (tasks == null) {
      tasks = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
