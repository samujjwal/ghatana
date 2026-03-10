package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ComplianceControlEvaluation agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that evaluates specific compliance controls input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ComplianceControlEvaluationInput(@NotNull String controlId, @NotNull String framework, @NotNull Map<String, Object> evidence) {
  public ComplianceControlEvaluationInput {
    if (controlId == null || controlId.isEmpty()) {
      throw new IllegalArgumentException("controlId cannot be null or empty");
    }
    if (framework == null || framework.isEmpty()) {
      throw new IllegalArgumentException("framework cannot be null or empty");
    }
    if (evidence == null) {
      evidence = Map.of();
    }
  }
}
