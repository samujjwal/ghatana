package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from incident response specialist.
 *
 * @doc.type record
 * @doc.purpose Incident response output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record IncidentResponseOutput(
    @NotNull String incidentId,
    @NotNull String status,
    @NotNull List<String> actionsTaken,
    @NotNull String runbookUrl,
    @NotNull Map<String, Object> metadata) {

  public IncidentResponseOutput {
    if (incidentId == null || incidentId.isEmpty()) {
      throw new IllegalArgumentException("incidentId cannot be null or empty");
    }
    if (status == null || status.isEmpty()) {
      throw new IllegalArgumentException("status cannot be null or empty");
    }
    if (actionsTaken == null) {
      actionsTaken = List.of();
    }
    if (runbookUrl == null) {
      runbookUrl = "";
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
