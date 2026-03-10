package com.ghatana.yappc.agent;

/**
 * Simplified budget specification for workflow step execution.
 *
 * <p>A convenience value object wrapping cost and time limits, without requiring
 * a token limit. Use instead of {@link Budget} when token budgeting is not needed.
 *
 * @param maxCostUsd     maximum cost in USD
 * @param maxWallTimeMs  maximum wall clock time in milliseconds
 * @doc.type record
 * @doc.purpose Simplified budget for steps that don't track token usage
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record StepBudget(double maxCostUsd, long maxWallTimeMs) {

  /**
   * Converts this simplified budget to a full {@link Budget} with zero token limit.
   *
   * @return a Budget equivalent of this StepBudget
   */
  public Budget toBudget() {
    return new Budget(0L, maxCostUsd, maxWallTimeMs);
  }
}
