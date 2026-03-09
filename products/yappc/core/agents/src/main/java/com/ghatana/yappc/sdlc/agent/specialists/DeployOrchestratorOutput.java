package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from DeployOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Orchestrates deployment pipelines and release workflows output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DeployOrchestratorOutput(@NotNull String deploymentId, @NotNull String status, @NotNull List<String> steps, @NotNull Map<String, Object> metadata) {
  public DeployOrchestratorOutput {
    if (deploymentId == null || deploymentId.isEmpty()) {
      throw new IllegalArgumentException("deploymentId cannot be null or empty");
    }
    if (status == null || status.isEmpty()) {
      throw new IllegalArgumentException("status cannot be null or empty");
    }
    if (steps == null) {
      steps = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
