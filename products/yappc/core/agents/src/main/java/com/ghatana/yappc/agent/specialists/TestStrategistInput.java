package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for TestStrategist agent.
 *
 * @doc.type record
 * @doc.purpose Expert test strategist for test planning and quality assurance strategy input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TestStrategistInput(@NotNull String projectId, @NotNull String codebaseProfile, @NotNull Map<String, Object> coverageData) {
  public TestStrategistInput {
    if (projectId == null || projectId.isEmpty()) {
      throw new IllegalArgumentException("projectId cannot be null or empty");
    }
    if (codebaseProfile == null || codebaseProfile.isEmpty()) {
      throw new IllegalArgumentException("codebaseProfile cannot be null or empty");
    }
    if (coverageData == null) {
      coverageData = Map.of();
    }
  }
}
