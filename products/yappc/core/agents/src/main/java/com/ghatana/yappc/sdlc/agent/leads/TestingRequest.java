package com.ghatana.yappc.sdlc.agent.leads;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for testing phase lead.
 *
 * @doc.type record
 * @doc.purpose Testing phase input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TestingRequest(
    @NotNull String implementationId,
    @NotNull List<String> targetSteps,
    @NotNull Map<String, Object> metadata) {

  public TestingRequest(@NotNull String implementationId, @NotNull List<String> targetSteps) {
    this(implementationId, targetSteps, Map.of());
  }

  public TestingRequest {
    if (implementationId == null || implementationId.isEmpty()) {
      throw new IllegalArgumentException("implementationId cannot be null or empty");
    }
    if (targetSteps == null) {
      targetSteps = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
