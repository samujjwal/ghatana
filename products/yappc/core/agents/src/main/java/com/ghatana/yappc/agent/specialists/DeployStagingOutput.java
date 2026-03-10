package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from deploy staging specialist.
 *
 * @doc.type record
 * @doc.purpose Staging deployment output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DeployStagingOutput(
    @NotNull String deploymentId,
    @NotNull String environment,
    @NotNull String status,
    @NotNull String deploymentUrl,
    @NotNull Map<String, Object> metadata) {

  public DeployStagingOutput {
    if (deploymentId == null || deploymentId.isEmpty()) {
      throw new IllegalArgumentException("deploymentId cannot be null or empty");
    }
    if (environment == null || environment.isEmpty()) {
      throw new IllegalArgumentException("environment cannot be null or empty");
    }
    if (status == null || status.isEmpty()) {
      throw new IllegalArgumentException("status cannot be null or empty");
    }
    if (deploymentUrl == null) {
      deploymentUrl = "";
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
