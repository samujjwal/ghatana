package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for CloudPilot agent.
 *
 * @doc.type record
 * @doc.purpose Expert cloud pilot for cloud architecture and resource management input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CloudPilotInput(@NotNull String environmentId, @NotNull String cloudProvider, @NotNull Map<String, Object> resourceSpec) {
  public CloudPilotInput {
    if (environmentId == null || environmentId.isEmpty()) {
      throw new IllegalArgumentException("environmentId cannot be null or empty");
    }
    if (cloudProvider == null || cloudProvider.isEmpty()) {
      throw new IllegalArgumentException("cloudProvider cannot be null or empty");
    }
    if (resourceSpec == null) {
      resourceSpec = Map.of();
    }
  }
}
