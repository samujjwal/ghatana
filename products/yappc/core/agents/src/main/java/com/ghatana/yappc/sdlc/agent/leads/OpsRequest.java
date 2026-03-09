package com.ghatana.yappc.sdlc.agent.leads;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Request for ops phase lead agent.
 *
 * @doc.type record
 * @doc.purpose Operations phase coordination input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record OpsRequest(@NotNull String deploymentId, @NotNull List<String> targetSteps) {
  public OpsRequest {
    if (deploymentId == null || deploymentId.isEmpty()) {
      throw new IllegalArgumentException("deploymentId cannot be null or empty");
    }
    if (targetSteps == null) {
      targetSteps = List.of();
    }
  }
}
