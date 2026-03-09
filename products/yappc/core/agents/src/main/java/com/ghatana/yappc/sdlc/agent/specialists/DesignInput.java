package com.ghatana.yappc.sdlc.agent.specialists;

import org.jetbrains.annotations.NotNull;

/**
 * Input for design specialist.
 *
 * @doc.type record
 * @doc.purpose Architecture design input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DesignInput(@NotNull String requirementsId, @NotNull String constraints) {
  public DesignInput {
    if (requirementsId == null || requirementsId.isEmpty()) {
      throw new IllegalArgumentException("requirementsId cannot be null or empty");
    }
    if (constraints == null) {
      constraints = "";
    }
  }
}
