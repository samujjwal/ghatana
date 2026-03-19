package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ApiHandlerGenerator agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that generates API handlers from OpenAPI/GraphQL specs input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ApiHandlerGeneratorInput(@NotNull String apiSpec, @NotNull String framework, @NotNull Map<String, Object> options) {
  public ApiHandlerGeneratorInput {
    if (apiSpec == null || apiSpec.isEmpty()) {
      throw new IllegalArgumentException("apiSpec cannot be null or empty");
    }
    if (framework == null || framework.isEmpty()) {
      throw new IllegalArgumentException("framework cannot be null or empty");
    }
    if (options == null) {
      options = Map.of();
    }
  }
}
