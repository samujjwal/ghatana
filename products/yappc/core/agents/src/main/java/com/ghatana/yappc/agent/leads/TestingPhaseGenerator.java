package com.ghatana.yappc.agent.leads;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.yappc.agent.StepResult;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.YAPPCAgentRegistry;
import io.activej.promise.Promise;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rule-based generator for testing phase orchestration.
 *
 * @doc.type class
 * @doc.purpose Testing phase orchestration logic
 * @doc.layer product
 * @doc.pattern Generator
 */
public class TestingPhaseGenerator
    implements OutputGenerator<StepRequest<TestingRequest>, StepResult<TestingResult>> {

  private static final Logger log = LoggerFactory.getLogger(TestingPhaseGenerator.class);

  private static final List<String> STEP_ORDER =
      List.of(
          "testing.deriveTestPlan",
          "testing.generateTests",
          "testing.executeTests",
          "testing.performanceTests",
          "testing.securityTests",
          "testing.releaseGate");

  private final YAPPCAgentRegistry agentRegistry;

  public TestingPhaseGenerator(@NotNull YAPPCAgentRegistry agentRegistry) {
    this.agentRegistry = agentRegistry;
  }

  @Override
  public @NotNull Promise<StepResult<TestingResult>> generate(
      @NotNull StepRequest<TestingRequest> input, @NotNull AgentContext context) {

    Instant startTime = Instant.now();
    TestingRequest request = input.input();

    log.info("Generating testing plan for implementation: {}", request.implementationId());

    // Determine execution order
    List<String> executionOrder = determineExecutionOrder(request.targetSteps());
    Map<String, TestingResult.TestResult> testResults = new LinkedHashMap<>();
    int totalTests = 0;
    int passedTests = 0;
    int failedTests = 0;

    log.info("Testing plan: {} test suites in order", executionOrder.size());

    // Execute test steps
    for (String step : executionOrder) {
      if (agentRegistry.hasAgent(step)) {
        log.info("Planning test suite: {}", step);
        // Simulate test results
        int tests = 10 + (int) (Math.random() * 20);
        int passed = (int) (tests * (0.95 + Math.random() * 0.05));
        int failed = tests - passed;

        totalTests += tests;
        passedTests += passed;
        failedTests += failed;

        testResults.put(
            step,
            new TestingResult.TestResult(
                step,
                failed == 0 ? "passed" : "failed",
                tests,
                passed,
                failed,
                100 + (long) (Math.random() * 500)));
      } else {
        log.warn("Test suite {} not registered, skipping", step);
      }
    }

    long totalDuration = Duration.between(startTime, Instant.now()).toMillis();
    boolean allPassed = failedTests == 0;

    TestingResult result =
        new TestingResult(
            testResults,
            totalTests,
            passedTests,
            failedTests,
            allPassed,
            Map.of(
                "phase", "testing",
                "totalSuites", executionOrder.size(),
                "coverage", "85%"));

    log.info(
        "Testing plan generated: {} suites, {} tests ({} passed, {} failed), {}ms",
        testResults.size(),
        totalTests,
        passedTests,
        failedTests,
        totalDuration);

    return Promise.of(
        StepResult.success(
            result,
            Map.of("generationTimeMs", totalDuration, "allPassed", allPassed),
            startTime,
            Instant.now()));
  }

  @Override
  public @NotNull Promise<Double> estimateCost(
      @NotNull StepRequest<TestingRequest> input, @NotNull AgentContext context) {
    return Promise.of(0.0);
  }

  @Override
  public @NotNull GeneratorMetadata getMetadata() {
    return GeneratorMetadata.builder()
        .name("TestingPhaseGenerator")
        .type("rule-based")
        .description("Orchestrates testing phase test suites")
        .version("1.0.0")
        .property("phase", "testing")
        .property("stepOrder", String.join(",", STEP_ORDER))
        .build();
  }

  private List<String> determineExecutionOrder(List<String> targetSteps) {
    if (targetSteps == null || targetSteps.isEmpty()) {
      return STEP_ORDER;
    }

    return STEP_ORDER.stream()
        .filter(step -> targetSteps.contains(step) || targetSteps.contains("*"))
        .toList();
  }
}
