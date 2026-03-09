package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from monitor specialist.
 *
 * @doc.type record
 * @doc.purpose Production monitoring output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record MonitorOutput(
    @NotNull String monitoringId,
    @NotNull String health,
    @NotNull List<String> alerts,
    @NotNull Map<String, Object> metrics,
    @NotNull Map<String, Object> metadata) {

  public MonitorOutput {
    if (monitoringId == null || monitoringId.isEmpty()) {
      throw new IllegalArgumentException("monitoringId cannot be null or empty");
    }
    if (health == null || health.isEmpty()) {
      throw new IllegalArgumentException("health cannot be null or empty");
    }
    if (alerts == null) {
      alerts = List.of();
    }
    if (metrics == null) {
      metrics = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
