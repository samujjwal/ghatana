package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for MultiCloudOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Multi-cloud orchestration request input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record MultiCloudOrchestratorInput(
    @NotNull String requestId,
    @NotNull String requestType,
    @NotNull List<String> targetProviders,
    @NotNull Map<String, Object> resourceSpec,
    @NotNull Map<String, Object> context) {

  /** Request type constants. */
  public static final String TYPE_DEPLOY = "deployment";
  public static final String TYPE_RESOURCE = "resource_allocation";
  public static final String TYPE_MIGRATION = "migration";
  public static final String TYPE_AUDIT = "audit";

  public MultiCloudOrchestratorInput {
    if (requestId == null || requestId.isEmpty()) {
      throw new IllegalArgumentException("requestId cannot be null or empty");
    }
    if (requestType == null || requestType.isEmpty()) {
      throw new IllegalArgumentException("requestType cannot be null or empty");
    }
    if (targetProviders == null || targetProviders.isEmpty()) {
      targetProviders = List.of("aws");
    }
    if (resourceSpec == null) {
      resourceSpec = Map.of();
    }
    if (context == null) {
      context = Map.of();
    }
  }
}
