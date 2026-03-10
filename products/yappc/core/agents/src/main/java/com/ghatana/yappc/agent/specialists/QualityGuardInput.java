package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for QualityGuard agent.
 *
 * @doc.type record
 * @doc.purpose Expert quality guard agent enforcing code and artifact quality standards input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record QualityGuardInput(@NotNull String artifactId, @NotNull String qualityProfile, @NotNull Map<String, Object> thresholds) {
  public QualityGuardInput {
    if (artifactId == null || artifactId.isEmpty()) {
      throw new IllegalArgumentException("artifactId cannot be null or empty");
    }
    if (qualityProfile == null || qualityProfile.isEmpty()) {
      throw new IllegalArgumentException("qualityProfile cannot be null or empty");
    }
    if (thresholds == null) {
      thresholds = Map.of();
    }
  }
}
