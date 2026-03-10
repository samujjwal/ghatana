package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for OperationsOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Operations orchestration request input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record OperationsOrchestratorInput(
    @NotNull String operationId,
    @NotNull String operationType,
    @NotNull String severity,
    @NotNull List<String> affectedServices,
    @NotNull Map<String, Object> context) {

  /** Operation type constants. */
  public static final String TYPE_MONITORING = "monitoring";
  public static final String TYPE_INCIDENT = "incident";
  public static final String TYPE_SLO_CHECK = "slo_check";
  public static final String TYPE_CAPACITY = "capacity";

  public OperationsOrchestratorInput {
    if (operationId == null || operationId.isEmpty()) {
      throw new IllegalArgumentException("operationId cannot be null or empty");
    }
    if (operationType == null || operationType.isEmpty()) {
      throw new IllegalArgumentException("operationType cannot be null or empty");
    }
    if (severity == null || severity.isEmpty()) {
      severity = "INFO";
    }
    if (affectedServices == null) {
      affectedServices = List.of();
    }
    if (context == null) {
      context = Map.of();
    }
  }
}
