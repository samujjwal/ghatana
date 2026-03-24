package com.ghatana.yappc.agents.architecture;

import java.util.List;
import java.util.Map;

/**
 * Output for OperationsOrchestratorAgent.
 */
public record OperationsOrchestratorOutput(
    String operationId,
    String status,
    List<String> actions,
    List<String> notifications,
    String incidentId,
    Map<String, Object> metadata
) {
    // Status constants
    public static final String STATUS_INCIDENT = "INCIDENT";
    public static final String STATUS_HEALTHY = "HEALTHY";
    public static final String STATUS_DEGRADED = "DEGRADED";
}
