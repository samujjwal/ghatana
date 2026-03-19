package com.ghatana.yappc.agent.specialists;

import org.jetbrains.annotations.NotNull;

/**
 * Input for scaffold specialist.
 *
 * @doc.type record
 * @doc.purpose Code scaffolding input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ScaffoldInput(@NotNull String architectureId, @NotNull String planId) {
  public ScaffoldInput {
    if (architectureId == null || architectureId.isEmpty()) {
      throw new IllegalArgumentException("architectureId cannot be null or empty");
    }
    if (planId == null || planId.isEmpty()) {
      throw new IllegalArgumentException("planId cannot be null or empty");
    }
  }
}
