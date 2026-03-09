package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for DependencyGate agent.
 *
 * @doc.type record
 * @doc.purpose Governance agent that enforces dependency policies and license compliance input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DependencyGateInput(@NotNull String projectId, @NotNull List<String> dependencies, @NotNull Map<String, Object> policies) {
  public DependencyGateInput {
    if (projectId == null || projectId.isEmpty()) {
      throw new IllegalArgumentException("projectId cannot be null or empty");
    }
    if (dependencies == null) {
      dependencies = List.of();
    }
    if (policies == null) {
      policies = Map.of();
    }
  }
}
