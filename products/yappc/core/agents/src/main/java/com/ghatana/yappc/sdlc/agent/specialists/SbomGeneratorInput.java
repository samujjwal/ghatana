package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for SbomGenerator agent.
 *
 * @doc.type record
 * @doc.purpose Release governance agent that generates Software Bill of Materials input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SbomGeneratorInput(@NotNull String projectId, @NotNull String buildId, @NotNull Map<String, Object> buildConfig) {
  public SbomGeneratorInput {
    if (projectId == null || projectId.isEmpty()) {
      throw new IllegalArgumentException("projectId cannot be null or empty");
    }
    if (buildId == null || buildId.isEmpty()) {
      throw new IllegalArgumentException("buildId cannot be null or empty");
    }
    if (buildConfig == null) {
      buildConfig = Map.of();
    }
  }
}
