package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from OperationsOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Operations orchestration result output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record OperationsOrchestratorOutput(
    @NotNull String operationId,
    @NotNull String status,
    @NotNull List<String> actionsExecuted,
    @NotNull List<String> notifications,
    @NotNull String incidentId,
    @NotNull Map<String, Object> metadata) {

  /** Operations status constants. */
  public static final String STATUS_HEALTHY = "HEALTHY";
  public static final String STATUS_DEGRADED = "DEGRADED";
  public static final String STATUS_INCIDENT = "INCIDENT";
  public static final String STATUS_RECOVERING = "RECOVERING";

  public OperationsOrchestratorOutput {
    if (operationId == null || operationId.isEmpty()) {
      throw new IllegalArgumentException("operationId cannot be null or empty");
    }
    if (status == null || status.isEmpty()) {
      throw new IllegalArgumentException("status cannot be null or empty");
    }
    if (actionsExecuted == null) {
      actionsExecuted = List.of();
    }
    if (notifications == null) {
      notifications = List.of();
    }
    if (incidentId == null) {
      incidentId = "";
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
