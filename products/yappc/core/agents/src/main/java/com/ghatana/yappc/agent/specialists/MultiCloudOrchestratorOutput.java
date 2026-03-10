package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from MultiCloudOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Multi-cloud orchestration result output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record MultiCloudOrchestratorOutput(
    @NotNull String planId,
    @NotNull String status,
    @NotNull List<String> providerActions,
    @NotNull Map<String, String> providerStatus,
    double estimatedCost,
    @NotNull Map<String, Object> metadata) {

  /** Multi-cloud status constants. */
  public static final String STATUS_PLANNED = "PLANNED";
  public static final String STATUS_EXECUTING = "EXECUTING";
  public static final String STATUS_COMPLETED = "COMPLETED";
  public static final String STATUS_PARTIAL_FAILURE = "PARTIAL_FAILURE";

  public MultiCloudOrchestratorOutput {
    if (planId == null || planId.isEmpty()) {
      throw new IllegalArgumentException("planId cannot be null or empty");
    }
    if (status == null || status.isEmpty()) {
      throw new IllegalArgumentException("status cannot be null or empty");
    }
    if (providerActions == null) {
      providerActions = List.of();
    }
    if (providerStatus == null) {
      providerStatus = Map.of();
    }
    if (estimatedCost < 0.0) {
      estimatedCost = 0.0;
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
