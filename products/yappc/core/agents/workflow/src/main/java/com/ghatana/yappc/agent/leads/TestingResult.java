package com.ghatana.yappc.agent.leads;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from testing phase lead.
 *
 * @doc.type record
 * @doc.purpose Testing phase output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TestingResult(
    @NotNull Map<String, TestResult> testResults,
    int totalTests,
    int passedTests,
    int failedTests,
    boolean allTestsPassed,
    @NotNull Map<String, Object> metadata) {

  public TestingResult {
    if (testResults == null) {
      testResults = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }

  public record TestResult(
      @NotNull String testSuite,
      @NotNull String status,
      int testCount,
      int passed,
      int failed,
      long durationMs) {}
}
