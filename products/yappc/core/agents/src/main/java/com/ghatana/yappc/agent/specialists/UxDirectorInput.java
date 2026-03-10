package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for UxDirector agent.
 *
 * @doc.type record
 * @doc.purpose Strategic UX director ensuring coherent user experience input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record UxDirectorInput(@NotNull String productId, @NotNull String uxChallenge, @NotNull Map<String, Object> userResearch) {
  public UxDirectorInput {
    if (productId == null || productId.isEmpty()) {
      throw new IllegalArgumentException("productId cannot be null or empty");
    }
    if (uxChallenge == null || uxChallenge.isEmpty()) {
      throw new IllegalArgumentException("uxChallenge cannot be null or empty");
    }
    if (userResearch == null) {
      userResearch = Map.of();
    }
  }
}
