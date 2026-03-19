package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from RollbackCoordinator agent.
 *
 * @doc.type record
 * @doc.purpose Release governance agent that coordinates rollback procedures output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RollbackCoordinatorOutput(@NotNull String rollbackId, @NotNull String status, @NotNull List<String> steps, @NotNull Map<String, Object> metadata) {
  public RollbackCoordinatorOutput {
    if (rollbackId == null || rollbackId.isEmpty()) {
      throw new IllegalArgumentException("rollbackId cannot be null or empty");
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
