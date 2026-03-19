package com.ghatana.yappc.agent.specialists;

import org.jetbrains.annotations.NotNull;

/**
 * Input for implement specialist.
 *
 * @doc.type record
 * @doc.purpose Code implementation input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ImplementInput(@NotNull String planId, @NotNull String unitName) {
  public ImplementInput {
    if (planId == null || planId.isEmpty()) {
      throw new IllegalArgumentException("planId cannot be null or empty");
    }
    if (unitName == null || unitName.isEmpty()) {
      throw new IllegalArgumentException("unitName cannot be null or empty");
    }
  }
}
