package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for RepoIntegration agent.
 *
 * @doc.type record
 * @doc.purpose Integration bridge agent for source code repository operations input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RepoIntegrationInput(@NotNull String repoUrl, @NotNull String operation, @NotNull Map<String, Object> params) {
  public RepoIntegrationInput {
    if (repoUrl == null || repoUrl.isEmpty()) {
      throw new IllegalArgumentException("repoUrl cannot be null or empty");
    }
    if (operation == null || operation.isEmpty()) {
      throw new IllegalArgumentException("operation cannot be null or empty");
    }
    if (params == null) {
      params = Map.of();
    }
  }
}
