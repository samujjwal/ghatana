package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ObservabilityIntegration agent.
 *
 * @doc.type record
 * @doc.purpose Integration bridge agent for observability platform data output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ObservabilityIntegrationOutput(@NotNull String queryId, @NotNull Map<String, Object> result, @NotNull Map<String, Object> metadata) {
  public ObservabilityIntegrationOutput {
    if (queryId == null || queryId.isEmpty()) {
      throw new IllegalArgumentException("queryId cannot be null or empty");
    }
    if (result == null) {
      result = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
