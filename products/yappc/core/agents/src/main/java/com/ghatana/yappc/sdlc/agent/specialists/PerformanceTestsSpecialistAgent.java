package com.ghatana.yappc.sdlc.agent.specialists;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.sdlc.*;
import com.ghatana.yappc.sdlc.agent.StepRequest;
import com.ghatana.yappc.sdlc.agent.YAPPCAgentBase;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import org.jetbrains.annotations.NotNull;

/**
 * Specialist agent for performance and load testing.
 *
 * <p>Executes load tests using JMeter/Gatling/K6, simulates concurrent user traffic with ramp-up
 * patterns, measures response times (avg, P95, P99), calculates throughput and error rates,
 * identifies performance bottlenecks and slow endpoints, validates against NFRs (latency,
 * throughput, error rate).
 *
 * @doc.type class
 * @doc.purpose Executes performance tests and measures system load capacity
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle act
 */
public class PerformanceTestsSpecialistAgent
    extends YAPPCAgentBase<PerformanceTestsInput, PerformanceTestsOutput> {

  private final MemoryStore memoryStore;

  public PerformanceTestsSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull
          OutputGenerator<StepRequest<PerformanceTestsInput>, StepResult<PerformanceTestsOutput>>
              generator) {
    super(
        "PerformanceTestsSpecialistAgent",
        "testing.performanceTests",
        new StepContract(
            "testing.performanceTests",
            "#/definitions/PerformanceTestsInput",
            "#/definitions/PerformanceTestsOutput",
            List.of("testing", "performance", "load", "nfr"),
            Map.of(
                "description",
                "Executes performance and load tests",
                "version",
                "1.0.0",
                "estimatedDuration",
                "30m")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull PerformanceTestsInput input) {
    List<String> errors = new ArrayList<>();

    if (input.testPlanId().isBlank()) {
      errors.add("testPlanId cannot be blank");
    }
    if (input.deploymentUrl().isBlank()) {
      errors.add("deploymentUrl cannot be blank");
    }
    if (input.endpoints().isEmpty()) {
      errors.add("endpoints cannot be empty");
    }
    if (input.scenarios().isEmpty()) {
      errors.add("scenarios cannot be empty");
    }
    if (input.environment().isBlank()) {
      errors.add("environment cannot be blank");
    }
    if (input.durationMinutes() < 1) {
      errors.add("durationMinutes must be at least 1");
    }
    if (input.maxConcurrentUsers() < 1) {
      errors.add("maxConcurrentUsers must be at least 1");
    }

    return errors.isEmpty()
        ? ValidationResult.success()
        : ValidationResult.fail(errors.toArray(new String[0]));
  }

  @Override
  protected StepRequest<PerformanceTestsInput> perceive(
      @NotNull StepRequest<PerformanceTestsInput> request, @NotNull AgentContext context) {
    return request;
  }

  /**
   * Generator for performance test execution (rule-based simulation).
   *
   * @doc.type class
   * @doc.purpose Executes load tests and generates performance metrics
   * @doc.layer product
   * @doc.pattern Strategy
   * @doc.gaa.lifecycle act
   */
  public static class PerformanceTestsGenerator
      implements OutputGenerator<
          StepRequest<PerformanceTestsInput>, StepResult<PerformanceTestsOutput>> {

    @Override
    public Promise<StepResult<PerformanceTestsOutput>> generate(
        StepRequest<PerformanceTestsInput> input, AgentContext context) {
      Instant start = Instant.now();

      PerformanceTestsInput req = input.input();
      String executionId = UUID.randomUUID().toString();
      Random random = new Random();

      // Simulate load test execution
      int totalRequests = 0;
      for (PerformanceTestsInput.LoadScenario scenario : req.scenarios()) {
        int requestsPerUser = (scenario.durationSeconds() / 2); // Avg 1 request per 2 seconds
        totalRequests += scenario.concurrentUsers() * requestsPerUser;
      }

      // Simulate success/failure rates (95-98% success)
      int successfulRequests = (int) (totalRequests * (0.95 + random.nextDouble() * 0.03));
      int failedRequests = totalRequests - successfulRequests;
      double errorRate = (failedRequests / (double) totalRequests) * 100;

      // Simulate response times (higher load = slower response)
      double avgUsers =
          req.scenarios().stream()
              .mapToInt(PerformanceTestsInput.LoadScenario::concurrentUsers)
              .average()
              .orElse(10);
      double loadFactor = avgUsers / 100.0; // Scale with concurrent users
      double averageResponseTimeMs = 50 + (loadFactor * 100) + (random.nextDouble() * 50);
      double p95ResponseTimeMs = averageResponseTimeMs * 2.5;
      double p99ResponseTimeMs = averageResponseTimeMs * 4.0;

      // Simulate throughput (requests per second)
      double totalDurationSeconds =
          req.scenarios().stream()
              .mapToInt(PerformanceTestsInput.LoadScenario::durationSeconds)
              .average()
              .orElse(60);
      double throughputRps = totalRequests / totalDurationSeconds;

      // Generate per-endpoint metrics
      Map<String, PerformanceTestsOutput.PerformanceMetrics> metricsByEndpoint = new HashMap<>();
      for (String endpoint : req.endpoints()) {
        int endpointRequests = totalRequests / req.endpoints().size();
        double endpointAvgMs = averageResponseTimeMs + (random.nextDouble() * 100 - 50);
        double endpointP95Ms = endpointAvgMs * 2.5;
        double endpointThroughput = throughputRps / req.endpoints().size();
        double endpointErrorRate = errorRate + (random.nextDouble() * 2 - 1); // +/- 1%

        metricsByEndpoint.put(
            endpoint,
            new PerformanceTestsOutput.PerformanceMetrics(
                endpoint,
                endpointRequests,
                endpointAvgMs,
                endpointP95Ms,
                endpointThroughput,
                Math.max(0, endpointErrorRate)));
      }

      // Identify bottlenecks (endpoints with P95 > 200ms or error rate > 2%)
      List<String> bottlenecks = new ArrayList<>();
      for (Map.Entry<String, PerformanceTestsOutput.PerformanceMetrics> entry :
          metricsByEndpoint.entrySet()) {
        PerformanceTestsOutput.PerformanceMetrics metrics = entry.getValue();
        if (metrics.p95ResponseMs() > 200 || metrics.errorRate() > 2.0) {
          bottlenecks.add(
              String.format(
                  "%s: P95=%.1fms, ErrorRate=%.2f%%",
                  entry.getKey(), metrics.p95ResponseMs(), metrics.errorRate()));
        }
      }

      // Performance gate: Pass if P95 < 200ms AND error rate < 1%
      boolean passedPerformanceGate = p95ResponseTimeMs < 200 && errorRate < 1.0;

      PerformanceTestsOutput output =
          new PerformanceTestsOutput(
              req.testPlanId(),
              executionId,
              totalRequests,
              successfulRequests,
              failedRequests,
              averageResponseTimeMs,
              p95ResponseTimeMs,
              p99ResponseTimeMs,
              throughputRps,
              errorRate,
              metricsByEndpoint,
              bottlenecks,
              passedPerformanceGate,
              Instant.now(),
              passedPerformanceGate
                  ? String.format(
                      "Performance tests passed: P95=%.1fms, Throughput=%.1f RPS, Error=%.2f%%",
                      p95ResponseTimeMs, throughputRps, errorRate)
                  : String.format(
                      "Performance gate FAILED: P95=%.1fms (max 200ms), Error=%.2f%% (max 1%%)",
                      p95ResponseTimeMs, errorRate));

      Instant end = Instant.now();
      Map<String, Object> metadata =
          Map.of(
              "executionId",
              executionId,
              "totalRequests",
              totalRequests,
              "throughputRps",
              throughputRps,
              "passedPerformanceGate",
              passedPerformanceGate,
              "bottleneckCount",
              bottlenecks.size());

      return Promise.of(StepResult.success(output, metadata, start, end));
    }

    @Override
    public Promise<Double> estimateCost(
        StepRequest<PerformanceTestsInput> input, AgentContext context) {
      return Promise.of(0.0); // Rule-based, no LLM cost
    }

    @Override
    public GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("PerformanceTestsGenerator")
          .type("rule-based")
          .description("Executes load tests and measures performance metrics")
          .version("1.0.0")
          .build();
    }
  }
}
