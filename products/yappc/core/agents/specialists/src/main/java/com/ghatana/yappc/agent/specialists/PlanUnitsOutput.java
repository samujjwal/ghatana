package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from plan units specialist.
 *
 * @doc.type record
 * @doc.purpose Implementation unit planning output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PlanUnitsOutput(
    @NotNull String planId,
    @NotNull List<String> implementationUnits,
    @NotNull Map<String, List<String>> dependencies,
    @NotNull Map<String, Integer> estimatedEffort,
    @NotNull Map<String, Object> metadata) {

  public PlanUnitsOutput {
    if (planId == null || planId.isEmpty()) {
      throw new IllegalArgumentException("planId cannot be null or empty");
    }
    if (implementationUnits == null) {
      implementationUnits = List.of();
    }
    if (dependencies == null) {
      dependencies = Map.of();
    }
    if (estimatedEffort == null) {
      estimatedEffort = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
