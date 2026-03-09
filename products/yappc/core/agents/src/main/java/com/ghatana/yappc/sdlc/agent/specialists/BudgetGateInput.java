package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for BudgetGate agent.
 *
 * @doc.type record
 * @doc.purpose Governance agent that enforces budget constraints on AI and cloud spend input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record BudgetGateInput(@NotNull String requestId, double estimatedCost, @NotNull String budgetCategory, @NotNull Map<String, Object> context) {
  public BudgetGateInput {
    if (requestId == null || requestId.isEmpty()) {
      throw new IllegalArgumentException("requestId cannot be null or empty");
    }
    if (budgetCategory == null || budgetCategory.isEmpty()) {
      throw new IllegalArgumentException("budgetCategory cannot be null or empty");
    }
    if (context == null) {
      context = Map.of();
    }
  }
}
