package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Input for PerformanceTestsSpecialistAgent.
 *
 * @doc.type record
 * @doc.purpose Input parameters for performance and load testing
 * @doc.layer product
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle act
 */
public record PerformanceTestsInput(
    @NotNull String testPlanId,
    @NotNull String deploymentUrl,
    @NotNull List<String> endpoints,
    @NotNull List<LoadScenario> scenarios,
    @NotNull String environment,
    int durationMinutes,
    int maxConcurrentUsers) {

  /**
   * Load test scenario.
   *
   * @doc.type record
   * @doc.purpose Performance test scenario configuration
   * @doc.layer product
   * @doc.pattern ValueObject
   */
  public record LoadScenario(
      String name, String type, int concurrentUsers, int rampUpSeconds, int durationSeconds) {}
}
