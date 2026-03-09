package com.ghatana.yappc.sdlc.agent.leads;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Result from ops phase lead agent.
 *
 * @doc.type record
 * @doc.purpose Operations phase coordination output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record OpsResult(
    @NotNull String deploymentId,
    @NotNull String status,
    @NotNull List<String> completedSteps,
    @NotNull Map<String, Object> monitoringData) {

  public OpsResult {
    if (deploymentId == null || deploymentId.isEmpty()) {
      throw new IllegalArgumentException("deploymentId cannot be null or empty");
    }
    if (status == null || status.isEmpty()) {
      throw new IllegalArgumentException("status cannot be null or empty");
    }
    if (completedSteps == null) {
      completedSteps = List.of();
    }
    if (monitoringData == null) {
      monitoringData = Map.of();
    }
  }
}
