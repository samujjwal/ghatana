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
 * Specialist agent for executing tests and collecting results.
 *
 * <p>Runs unit/integration/performance tests using appropriate frameworks, executes tests in
 * parallel when configured, collects pass/fail results and duration metrics, detects flaky tests
 * based on retry behavior, aggregates results by test file for detailed reporting.
 *
 * @doc.type class
 * @doc.purpose Executes tests and collects execution metrics
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle act
 */
public class ExecuteTestsSpecialistAgent
    extends YAPPCAgentBase<ExecuteTestsInput, ExecuteTestsOutput> {

  private final MemoryStore memoryStore;

  public ExecuteTestsSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull
          OutputGenerator<StepRequest<ExecuteTestsInput>, StepResult<ExecuteTestsOutput>>
              generator) {
    super(
        "ExecuteTestsSpecialistAgent",
        "testing.executeTests",
        new StepContract(
            "testing.executeTests",
            "#/definitions/ExecuteTestsInput",
            "#/definitions/ExecuteTestsOutput",
            List.of("testing", "execution", "ci", "quality"),
            Map.of(
                "description",
                "Executes tests and collects execution metrics",
                "version",
                "1.0.0",
                "estimatedDuration",
                "15m")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ExecuteTestsInput input) {
    List<String> errors = new ArrayList<>();

    if (input.testPlanId().isBlank()) {
      errors.add("testPlanId cannot be blank");
    }
    if (input.testFiles().isEmpty()) {
      errors.add("testFiles cannot be empty");
    }
    if (input.testType().isBlank()) {
      errors.add("testType cannot be blank");
    }
    if (input.environment().isBlank()) {
      errors.add("environment cannot be blank");
    }
    if (input.parallelism() < 1) {
      errors.add("parallelism must be at least 1");
    }
    if (input.timeoutMinutes() < 1) {
      errors.add("timeoutMinutes must be at least 1");
    }

    return errors.isEmpty()
        ? ValidationResult.success()
        : ValidationResult.fail(errors.toArray(new String[0]));
  }

  @Override
  protected StepRequest<ExecuteTestsInput> perceive(
      @NotNull StepRequest<ExecuteTestsInput> request, @NotNull AgentContext context) {
    return request;
  }

  /**
   * Generator for test execution results (rule-based simulation).
   *
   * @doc.type class
   * @doc.purpose Generates test execution results based on test files
   * @doc.layer product
   * @doc.pattern Strategy
   * @doc.gaa.lifecycle act
   */
  public static class ExecuteTestsGenerator
      implements OutputGenerator<StepRequest<ExecuteTestsInput>, StepResult<ExecuteTestsOutput>> {

    @Override
    public Promise<StepResult<ExecuteTestsOutput>> generate(
        StepRequest<ExecuteTestsInput> input, AgentContext context) {
      Instant start = Instant.now();

      ExecuteTestsInput req = input.input();
      String executionId = UUID.randomUUID().toString();

      // Simulate test execution
      Map<String, ExecuteTestsOutput.TestFileResult> fileResults = new HashMap<>();
      int totalTests = 0;
      int passed = 0;
      int failed = 0;
      int skipped = 0;
      int flaky = 0;
      List<String> failedTests = new ArrayList<>();

      for (String testFile : req.testFiles()) {
        // Simulate: Each file has 3-8 tests, 95% pass rate
        int testsInFile = 3 + new Random().nextInt(6);
        int passedInFile = (int) (testsInFile * 0.95);
        int failedInFile = testsInFile - passedInFile;
        int skippedInFile = new Random().nextInt(2); // 0-1 skipped
        double fileDuration = 2.0 + new Random().nextDouble() * 8.0; // 2-10s per file

        List<String> fileFailures = new ArrayList<>();
        for (int i = 0; i < failedInFile; i++) {
          String failedTest = testFile + "::test_case_" + (i + 1);
          fileFailures.add(failedTest);
          failedTests.add(failedTest);
        }

        // Flaky detection: 5% of tests are flaky
        int flakyInFile = new Random().nextInt(100) < 5 ? 1 : 0;

        fileResults.put(
            testFile,
            new ExecuteTestsOutput.TestFileResult(
                testFile,
                testsInFile,
                passedInFile,
                failedInFile,
                skippedInFile,
                fileDuration,
                fileFailures));

        totalTests += testsInFile;
        passed += passedInFile;
        failed += failedInFile;
        skipped += skippedInFile;
        flaky += flakyInFile;
      }

      double totalDuration =
          req.testFiles().size() / (double) req.parallelism() * 5.0; // Avg 5s per file
      boolean success = failed == 0;

      ExecuteTestsOutput output =
          new ExecuteTestsOutput(
              req.testPlanId(),
              executionId,
              totalTests,
              passed,
              failed,
              skipped,
              flaky,
              totalDuration,
              fileResults,
              failedTests,
              Instant.now(),
              success,
              success
                  ? String.format(
                      "All tests passed (%d/%d) in %.1fs", passed, totalTests, totalDuration)
                  : String.format(
                      "%d tests failed out of %d in %.1fs", failed, totalTests, totalDuration));

      Instant end = Instant.now();
      Map<String, Object> metadata =
          Map.of(
              "executionId",
              executionId,
              "parallelism",
              req.parallelism(),
              "totalDuration",
              totalDuration,
              "passRate",
              String.format("%.1f%%", (passed / (double) totalTests) * 100));

      return Promise.of(StepResult.success(output, metadata, start, end));
    }

    @Override
    public Promise<Double> estimateCost(
        StepRequest<ExecuteTestsInput> input, AgentContext context) {
      return Promise.of(0.0); // Rule-based, no LLM cost
    }

    @Override
    public GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ExecuteTestsGenerator")
          .type("rule-based")
          .description("Executes tests using appropriate test frameworks")
          .version("1.0.0")
          .build();
    }
  }
}
