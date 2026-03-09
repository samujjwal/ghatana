package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ObservabilityIntegration agent.
 *
 * @doc.type record
 * @doc.purpose Integration bridge agent for observability platform data input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ObservabilityIntegrationInput(@NotNull String platformId, @NotNull String queryType, @NotNull Map<String, Object> query) {
  public ObservabilityIntegrationInput {
    if (platformId == null || platformId.isEmpty()) {
      throw new IllegalArgumentException("platformId cannot be null or empty");
    }
    if (queryType == null || queryType.isEmpty()) {
      throw new IllegalArgumentException("queryType cannot be null or empty");
    }
    if (query == null) {
      query = Map.of();
    }
  }
}
