package com.ghatana.yappc.sdlc.agent.specialists;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from PerformanceTestsSpecialistAgent.
 *
 * @doc.type record
 * @doc.purpose Performance test execution results with metrics
 * @doc.layer product
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle act
 */
public record PerformanceTestsOutput(
    @NotNull String testPlanId,
    @NotNull String executionId,
    int totalRequests,
    int successfulRequests,
    int failedRequests,
    double averageResponseTimeMs,
    double p95ResponseTimeMs,
    double p99ResponseTimeMs,
    double throughputRps,
    double errorRate,
    @NotNull Map<String, PerformanceMetrics> metricsByEndpoint,
    @NotNull List<String> bottlenecks,
    boolean passedPerformanceGate,
    @NotNull Instant executedAt,
    String message) {

  /**
   * Performance metrics for an endpoint.
   *
   * @doc.type record
   * @doc.purpose Per-endpoint performance metrics
   * @doc.layer product
   * @doc.pattern ValueObject
   */
  public record PerformanceMetrics(
      String endpoint,
      int requests,
      double avgResponseMs,
      double p95ResponseMs,
      double throughputRps,
      double errorRate) {}
}
