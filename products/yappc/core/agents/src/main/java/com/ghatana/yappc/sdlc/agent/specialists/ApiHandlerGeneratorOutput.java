package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ApiHandlerGenerator agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that generates API handlers from OpenAPI/GraphQL specs output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ApiHandlerGeneratorOutput(@NotNull String generatedCode, @NotNull List<String> filePaths, @NotNull List<String> routes, @NotNull Map<String, Object> metadata) {
  public ApiHandlerGeneratorOutput {
    if (generatedCode == null || generatedCode.isEmpty()) {
      throw new IllegalArgumentException("generatedCode cannot be null or empty");
    }
    if (filePaths == null) {
      filePaths = List.of();
    }
    if (routes == null) {
      routes = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
