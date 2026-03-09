package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from build specialist.
 *
 * @doc.type record
 * @doc.purpose Build execution output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record BuildOutput(
    @NotNull String buildId,
    @NotNull boolean success,
    @NotNull List<String> artifacts,
    @NotNull Map<String, Object> buildMetrics,
    @NotNull Map<String, Object> metadata) {

  public BuildOutput {
    if (buildId == null || buildId.isEmpty()) {
      throw new IllegalArgumentException("buildId cannot be null or empty");
    }
    if (artifacts == null) {
      artifacts = List.of();
    }
    if (buildMetrics == null) {
      buildMetrics = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
