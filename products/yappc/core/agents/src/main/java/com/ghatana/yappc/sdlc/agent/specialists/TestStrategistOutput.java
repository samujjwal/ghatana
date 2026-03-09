package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from TestStrategist agent.
 *
 * @doc.type record
 * @doc.purpose Expert test strategist for test planning and quality assurance strategy output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TestStrategistOutput(@NotNull String strategyId, @NotNull String testPlan, @NotNull List<String> testTypes, @NotNull Map<String, Object> metadata) {
  public TestStrategistOutput {
    if (strategyId == null || strategyId.isEmpty()) {
      throw new IllegalArgumentException("strategyId cannot be null or empty");
    }
    if (testPlan == null || testPlan.isEmpty()) {
      throw new IllegalArgumentException("testPlan cannot be null or empty");
    }
    if (testTypes == null) {
      testTypes = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
