package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for DatabaseIntegration agent.
 *
 * @doc.type record
 * @doc.purpose Integration bridge agent for database connectivity and operations input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DatabaseIntegrationInput(@NotNull String connectionId, @NotNull String operation, @NotNull Map<String, Object> params) {
  public DatabaseIntegrationInput {
    if (connectionId == null || connectionId.isEmpty()) {
      throw new IllegalArgumentException("connectionId cannot be null or empty");
    }
    if (operation == null || operation.isEmpty()) {
      throw new IllegalArgumentException("operation cannot be null or empty");
    }
    if (params == null) {
      params = Map.of();
    }
  }
}
