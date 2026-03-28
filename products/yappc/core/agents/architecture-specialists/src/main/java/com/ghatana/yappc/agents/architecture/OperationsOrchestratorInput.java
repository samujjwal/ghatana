package com.ghatana.yappc.agents.architecture;

import java.util.List;
import java.util.Map;

/**
 * Input for OperationsOrchestratorAgent.
 *
 * @doc.type record
 * @doc.purpose Input contract for operations orchestration agent
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record OperationsOrchestratorInput(
    String operationId,
    String operationType,
    String severity,
    List<String> affectedServices,
    Map<String, Object> context
) {}
