package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for CloudResourceRisk agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that assesses risk posture of cloud resources input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CloudResourceRiskInput(@NotNull String resourceId, @NotNull String resourceType, @NotNull Map<String, Object> config) {
  public CloudResourceRiskInput {
    if (resourceId == null || resourceId.isEmpty()) {
      throw new IllegalArgumentException("resourceId cannot be null or empty");
    }
    if (resourceType == null || resourceType.isEmpty()) {
      throw new IllegalArgumentException("resourceType cannot be null or empty");
    }
    if (config == null) {
      config = Map.of();
    }
  }
}
