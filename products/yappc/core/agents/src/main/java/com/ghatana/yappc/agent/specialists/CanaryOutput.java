package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from canary specialist.
 *
 * @doc.type record
 * @doc.purpose Canary deployment output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CanaryOutput(
    @NotNull String canaryId,
    @NotNull String status,
    @NotNull double errorRate,
    @NotNull double latencyP95,
    @NotNull Map<String, Object> metrics,
    @NotNull Map<String, Object> metadata) {

  public CanaryOutput {
    if (canaryId == null || canaryId.isEmpty()) {
      throw new IllegalArgumentException("canaryId cannot be null or empty");
    }
    if (status == null || status.isEmpty()) {
      throw new IllegalArgumentException("status cannot be null or empty");
    }
    if (metrics == null) {
      metrics = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
