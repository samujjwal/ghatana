package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for SecurityPostureOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Orchestrates security posture assessment across the organization input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SecurityPostureOrchestratorInput(@NotNull String organizationId, @NotNull List<String> assessmentScope, @NotNull Map<String, Object> policies) {
  public SecurityPostureOrchestratorInput {
    if (organizationId == null || organizationId.isEmpty()) {
      throw new IllegalArgumentException("organizationId cannot be null or empty");
    }
    if (assessmentScope == null) {
      assessmentScope = List.of();
    }
    if (policies == null) {
      policies = Map.of();
    }
  }
}
