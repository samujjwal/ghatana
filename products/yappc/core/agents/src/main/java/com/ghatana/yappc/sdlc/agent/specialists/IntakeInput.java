package com.ghatana.yappc.sdlc.agent.specialists;

import org.jetbrains.annotations.NotNull;

/**
 * Input for intake specialist.
 *
 * @doc.type record
 * @doc.purpose Requirements intake input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record IntakeInput(@NotNull String requirements, @NotNull String source) {
  public IntakeInput {
    if (requirements == null || requirements.isEmpty()) {
      throw new IllegalArgumentException("requirements cannot be null or empty");
    }
    if (source == null || source.isEmpty()) {
      source = "unknown";
    }
  }
}
