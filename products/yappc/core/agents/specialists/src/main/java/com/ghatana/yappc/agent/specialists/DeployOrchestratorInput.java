package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for DeployOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Orchestrates deployment pipelines and release workflows input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DeployOrchestratorInput(@NotNull String releaseId, @NotNull String targetEnvironment, @NotNull Map<String, Object> deploymentConfig) {
  public DeployOrchestratorInput {
    if (releaseId == null || releaseId.isEmpty()) {
      throw new IllegalArgumentException("releaseId cannot be null or empty");
    }
    if (targetEnvironment == null || targetEnvironment.isEmpty()) {
      throw new IllegalArgumentException("targetEnvironment cannot be null or empty");
    }
    if (deploymentConfig == null) {
      deploymentConfig = Map.of();
    }
  }
}
