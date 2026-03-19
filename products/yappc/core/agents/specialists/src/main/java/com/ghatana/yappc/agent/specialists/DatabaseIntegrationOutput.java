package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from DatabaseIntegration agent.
 *
 * @doc.type record
 * @doc.purpose Integration bridge agent for database connectivity and operations output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DatabaseIntegrationOutput(@NotNull String operationId, @NotNull String result, @NotNull Map<String, Object> metadata) {
  public DatabaseIntegrationOutput {
    if (operationId == null || operationId.isEmpty()) {
      throw new IllegalArgumentException("operationId cannot be null or empty");
    }
    if (result == null || result.isEmpty()) {
      throw new IllegalArgumentException("result cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
