package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ComplianceControlEvaluation agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that evaluates specific compliance controls output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ComplianceControlEvaluationOutput(@NotNull String evaluationId, @NotNull String status, @NotNull List<String> gaps, @NotNull Map<String, Object> metadata) {
  public ComplianceControlEvaluationOutput {
    if (evaluationId == null || evaluationId.isEmpty()) {
      throw new IllegalArgumentException("evaluationId cannot be null or empty");
    }
    if (status == null || status.isEmpty()) {
      throw new IllegalArgumentException("status cannot be null or empty");
    }
    if (gaps == null) {
      gaps = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
