package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from CloudPilot agent.
 *
 * @doc.type record
 * @doc.purpose Expert cloud pilot for cloud architecture and resource management output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CloudPilotOutput(@NotNull String planId, @NotNull String recommendation, @NotNull Map<String, Object> costEstimate, @NotNull Map<String, Object> metadata) {
  public CloudPilotOutput {
    if (planId == null || planId.isEmpty()) {
      throw new IllegalArgumentException("planId cannot be null or empty");
    }
    if (recommendation == null || recommendation.isEmpty()) {
      throw new IllegalArgumentException("recommendation cannot be null or empty");
    }
    if (costEstimate == null) {
      costEstimate = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
