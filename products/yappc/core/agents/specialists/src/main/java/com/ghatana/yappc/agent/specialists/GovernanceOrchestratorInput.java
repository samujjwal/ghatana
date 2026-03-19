package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for GovernanceOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Governance orchestration request input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record GovernanceOrchestratorInput(
    @NotNull String requestId,
    @NotNull String requestType,
    @NotNull String targetEntity,
    @NotNull List<String> policiesRequired,
    @NotNull Map<String, Object> context) {

  public GovernanceOrchestratorInput {
    if (requestId == null || requestId.isEmpty()) {
      throw new IllegalArgumentException("requestId cannot be null or empty");
    }
    if (requestType == null || requestType.isEmpty()) {
      throw new IllegalArgumentException("requestType cannot be null or empty");
    }
    if (targetEntity == null || targetEntity.isEmpty()) {
      targetEntity = "unknown";
    }
    if (policiesRequired == null) {
      policiesRequired = List.of();
    }
    if (context == null) {
      context = Map.of();
    }
  }
}
