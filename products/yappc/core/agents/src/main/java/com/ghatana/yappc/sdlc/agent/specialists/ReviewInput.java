package com.ghatana.yappc.sdlc.agent.specialists;

import org.jetbrains.annotations.NotNull;

/**
 * Input for review specialist.
 *
 * @doc.type record
 * @doc.purpose Code review input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReviewInput(@NotNull String implementationId, @NotNull String unitName) {
  public ReviewInput {
    if (implementationId == null || implementationId.isEmpty()) {
      throw new IllegalArgumentException("implementationId cannot be null or empty");
    }
    if (unitName == null || unitName.isEmpty()) {
      throw new IllegalArgumentException("unitName cannot be null or empty");
    }
  }
}
