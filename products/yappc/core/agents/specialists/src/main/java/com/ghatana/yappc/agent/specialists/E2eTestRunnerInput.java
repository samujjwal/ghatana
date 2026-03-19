package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for E2eTestRunner agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that executes end-to-end test suites and reports results input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record E2eTestRunnerInput(@NotNull String testSuiteId, @NotNull String targetEnvironment, @NotNull Map<String, Object> config) {
  public E2eTestRunnerInput {
    if (testSuiteId == null || testSuiteId.isEmpty()) {
      throw new IllegalArgumentException("testSuiteId cannot be null or empty");
    }
    if (targetEnvironment == null || targetEnvironment.isEmpty()) {
      throw new IllegalArgumentException("targetEnvironment cannot be null or empty");
    }
    if (config == null) {
      config = Map.of();
    }
  }
}
