package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for RollbackCoordinator agent.
 *
 * @doc.type record
 * @doc.purpose Release governance agent that coordinates rollback procedures input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RollbackCoordinatorInput(@NotNull String deploymentId, @NotNull String reason, @NotNull Map<String, Object> rollbackConfig) {
  public RollbackCoordinatorInput {
    if (deploymentId == null || deploymentId.isEmpty()) {
      throw new IllegalArgumentException("deploymentId cannot be null or empty");
    }
    if (reason == null || reason.isEmpty()) {
      throw new IllegalArgumentException("reason cannot be null or empty");
    }
    if (rollbackConfig == null) {
      rollbackConfig = Map.of();
    }
  }
}
