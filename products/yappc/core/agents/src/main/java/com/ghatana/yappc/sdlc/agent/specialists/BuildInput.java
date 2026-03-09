package com.ghatana.yappc.sdlc.agent.specialists;

import org.jetbrains.annotations.NotNull;

/**
 * Input for build specialist.
 *
 * @doc.type record
 * @doc.purpose Build execution input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record BuildInput(@NotNull String implementationId, @NotNull String buildTarget) {
  public BuildInput {
    if (implementationId == null || implementationId.isEmpty()) {
      throw new IllegalArgumentException("implementationId cannot be null or empty");
    }
    if (buildTarget == null) {
      buildTarget = "default";
    }
  }
}
