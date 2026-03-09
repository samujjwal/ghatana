package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ArtifactStoreIntegration agent.
 *
 * @doc.type record
 * @doc.purpose Integration bridge agent for artifact repository operations input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ArtifactStoreIntegrationInput(@NotNull String storeId, @NotNull String operation, @NotNull Map<String, Object> artifactData) {
  public ArtifactStoreIntegrationInput {
    if (storeId == null || storeId.isEmpty()) {
      throw new IllegalArgumentException("storeId cannot be null or empty");
    }
    if (operation == null || operation.isEmpty()) {
      throw new IllegalArgumentException("operation cannot be null or empty");
    }
    if (artifactData == null) {
      artifactData = Map.of();
    }
  }
}
