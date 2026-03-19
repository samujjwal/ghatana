package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from promote or rollback specialist.
 *
 * @doc.type record
 * @doc.purpose Promotion/rollback decision output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PromoteOrRollbackOutput(
    @NotNull String decisionId,
    @NotNull String decision,
    @NotNull String reason,
    @NotNull int trafficPercentage,
    @NotNull Map<String, Object> metadata) {

  public PromoteOrRollbackOutput {
    if (decisionId == null || decisionId.isEmpty()) {
      throw new IllegalArgumentException("decisionId cannot be null or empty");
    }
    if (decision == null || decision.isEmpty()) {
      throw new IllegalArgumentException("decision cannot be null or empty");
    }
    if (reason == null) {
      reason = "";
    }
    if (trafficPercentage < 0 || trafficPercentage > 100) {
      throw new IllegalArgumentException("trafficPercentage must be between 0 and 100");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
