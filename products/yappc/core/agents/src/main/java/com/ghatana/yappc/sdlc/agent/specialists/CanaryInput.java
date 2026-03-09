package com.ghatana.yappc.sdlc.agent.specialists;

import org.jetbrains.annotations.NotNull;

/**
 * Input for canary specialist.
 *
 * @doc.type record
 * @doc.purpose Canary deployment input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CanaryInput(
    @NotNull String deploymentId, @NotNull int trafficPercentage, @NotNull int durationMinutes) {
  public CanaryInput {
    if (deploymentId == null || deploymentId.isEmpty()) {
      throw new IllegalArgumentException("deploymentId cannot be null or empty");
    }
    if (trafficPercentage < 0 || trafficPercentage > 100) {
      throw new IllegalArgumentException("trafficPercentage must be between 0 and 100");
    }
    if (durationMinutes <= 0) {
      throw new IllegalArgumentException("durationMinutes must be positive");
    }
  }
}
