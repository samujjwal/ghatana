package com.ghatana.yappc.agent.specialists;

import org.jetbrains.annotations.NotNull;

/**
 * Input for plan units specialist.
 *
 * @doc.type record
 * @doc.purpose Implementation unit planning input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PlanUnitsInput(@NotNull String scaffoldId, @NotNull String architectureId) {
  public PlanUnitsInput {
    if (scaffoldId == null || scaffoldId.isEmpty()) {
      throw new IllegalArgumentException("scaffoldId cannot be null or empty");
    }
    if (architectureId == null || architectureId.isEmpty()) {
      throw new IllegalArgumentException("architectureId cannot be null or empty");
    }
  }
}
