package com.ghatana.yappc.agents.architecture;

import java.util.List;
import java.util.Map;

/**
 * Input for OperationsOrchestratorAgent.
 */
public record OperationsOrchestratorInput(
    String operationId,
    String operationType,
    String severity,
    List<String> affectedServices,
    Map<String, Object> context
) {}
