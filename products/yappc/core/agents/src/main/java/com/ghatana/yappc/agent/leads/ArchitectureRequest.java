package com.ghatana.yappc.agent.leads;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Request for architecture phase coordination.
 *
 * @param requirements high-level requirements
 * @param targetSteps architecture steps to execute
 * @param metadata additional metadata
 * @doc.type record
 * @doc.purpose Input for architecture phase lead
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ArchitectureRequest(
    @NotNull String requirements,
    @NotNull List<String> targetSteps,
    @NotNull Map<String, Object> metadata) {

  public ArchitectureRequest {
    if (requirements == null || requirements.isEmpty()) {
      throw new IllegalArgumentException("requirements cannot be null or empty");
    }
    if (targetSteps == null || targetSteps.isEmpty()) {
      throw new IllegalArgumentException("targetSteps cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }

  public ArchitectureRequest(@NotNull String requirements, @NotNull List<String> targetSteps) {
    this(requirements, targetSteps, Map.of());
  }
}
