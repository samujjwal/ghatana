package com.ghatana.yappc.sdlc.agent.specialists;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ExecuteTestsSpecialistAgent.
 *
 * @doc.type record
 * @doc.purpose Test execution results with detailed metrics
 * @doc.layer product
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle act
 */
public record ExecuteTestsOutput(
    @NotNull String testPlanId,
    @NotNull String executionId,
    int totalTests,
    int passed,
    int failed,
    int skipped,
    int flaky,
    double durationSeconds,
    @NotNull Map<String, TestFileResult> testFileResults,
    @NotNull List<String> failedTests,
    @NotNull Instant executedAt,
    boolean success,
    String message) {

  /**
   * Result for a single test file.
   *
   * @doc.type record
   * @doc.purpose Individual test file execution result
   * @doc.layer product
   * @doc.pattern ValueObject
   */
  public record TestFileResult(
      String filePath,
      int tests,
      int passed,
      int failed,
      int skipped,
      double durationSeconds,
      List<String> failures) {}
}
