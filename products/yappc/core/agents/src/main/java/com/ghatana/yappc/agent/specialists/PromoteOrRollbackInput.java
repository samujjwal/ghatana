package com.ghatana.yappc.agent.specialists;

import org.jetbrains.annotations.NotNull;

/**
 * Input for promote or rollback specialist.
 *
 * @doc.type record
 * @doc.purpose Promotion/rollback decision input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PromoteOrRollbackInput(@NotNull String canaryId, @NotNull double errorRateThreshold) {
  public PromoteOrRollbackInput {
    if (canaryId == null || canaryId.isEmpty()) {
      throw new IllegalArgumentException("canaryId cannot be null or empty");
    }
    if (errorRateThreshold < 0 || errorRateThreshold > 1) {
      throw new IllegalArgumentException("errorRateThreshold must be between 0 and 1");
    }
  }
}
