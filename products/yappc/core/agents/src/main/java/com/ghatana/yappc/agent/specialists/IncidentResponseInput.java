package com.ghatana.yappc.agent.specialists;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Input for incident response specialist.
 *
 * @doc.type record
 * @doc.purpose Incident response input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record IncidentResponseInput(
    @NotNull String deploymentId, @NotNull String severity, @NotNull List<String> symptoms) {
  public IncidentResponseInput {
    if (deploymentId == null || deploymentId.isEmpty()) {
      throw new IllegalArgumentException("deploymentId cannot be null or empty");
    }
    if (severity == null || severity.isEmpty()) {
      throw new IllegalArgumentException("severity cannot be null or empty");
    }
    if (symptoms == null) {
      symptoms = List.of();
    }
  }
}
