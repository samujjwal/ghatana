package com.ghatana.yappc.agent.leads;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Request for implementation phase.
 *
 * @doc.type record
 * @doc.purpose Input for implementation phase lead
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ImplementationRequest(
    @NotNull String architecture,
    @NotNull List<String> targetSteps,
    @NotNull Map<String, Object> metadata) {

  public ImplementationRequest {
    if (architecture == null || architecture.isEmpty()) {
      throw new IllegalArgumentException("architecture cannot be null or empty");
    }
    if (targetSteps == null) {
      targetSteps = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
