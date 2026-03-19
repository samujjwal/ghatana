package com.ghatana.yappc.agent.specialists;

import org.jetbrains.annotations.NotNull;

/**
 * Input for monitor specialist.
 *
 * @doc.type record
 * @doc.purpose Production monitoring input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record MonitorInput(@NotNull String deploymentId, @NotNull int durationMinutes) {
  public MonitorInput {
    if (deploymentId == null || deploymentId.isEmpty()) {
      throw new IllegalArgumentException("deploymentId cannot be null or empty");
    }
    if (durationMinutes <= 0) {
      throw new IllegalArgumentException("durationMinutes must be positive");
    }
  }
}
