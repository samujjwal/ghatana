package com.ghatana.yappc.sdlc.agent.specialists;

import org.jetbrains.annotations.NotNull;

/**
 * Input for deploy staging specialist.
 *
 * @doc.type record
 * @doc.purpose Staging deployment input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DeployStagingInput(@NotNull String buildId, @NotNull String environment) {
  public DeployStagingInput {
    if (buildId == null || buildId.isEmpty()) {
      throw new IllegalArgumentException("buildId cannot be null or empty");
    }
    if (environment == null) {
      environment = "staging";
    }
  }
}
