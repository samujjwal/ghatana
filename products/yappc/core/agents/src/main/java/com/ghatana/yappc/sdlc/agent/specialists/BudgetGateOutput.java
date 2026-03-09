package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from BudgetGate agent.
 *
 * @doc.type record
 * @doc.purpose Governance agent that enforces budget constraints on AI and cloud spend output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record BudgetGateOutput(@NotNull String gateId, boolean approved, double remainingBudget, @NotNull Map<String, Object> metadata) {
  public BudgetGateOutput {
    if (gateId == null || gateId.isEmpty()) {
      throw new IllegalArgumentException("gateId cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
