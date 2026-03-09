package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for FeatureFlagIntegration agent.
 *
 * @doc.type record
 * @doc.purpose Integration bridge agent for feature flag management systems input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record FeatureFlagIntegrationInput(@NotNull String flagServiceId, @NotNull String operation, @NotNull Map<String, Object> flagData) {
  public FeatureFlagIntegrationInput {
    if (flagServiceId == null || flagServiceId.isEmpty()) {
      throw new IllegalArgumentException("flagServiceId cannot be null or empty");
    }
    if (operation == null || operation.isEmpty()) {
      throw new IllegalArgumentException("operation cannot be null or empty");
    }
    if (flagData == null) {
      flagData = Map.of();
    }
  }
}
