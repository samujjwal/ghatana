package com.ghatana.yappc.agents.testing;

import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Detects flaky tests by analyzing inconsistent execution history and generating repair hints
 * @doc.layer product
 * @doc.pattern Analyzer
 */
public final class FlakyTestDetector {
  private static final double FLAKINESS_THRESHOLD = 0.10;

  private final YAPPCAIService aiService;

  public FlakyTestDetector() {
    this.aiService = null;
  }

  public FlakyTestDetector(YAPPCAIService aiService) {
    this.aiService = Objects.requireNonNull(aiService, "aiService");
  }

  public Promise<List<FlakyTestReport>> detectFlakyTests(List<TestRunRecord> history) {
    if (history == null || history.isEmpty()) {
      return Promise.of(List.of());
    }

    Map<String, List<TestRunRecord>> grouped = new HashMap<>();
    for (TestRunRecord record : history) {
      grouped.computeIfAbsent(record.testId(), ignored -> new ArrayList<>()).add(record);
    }

    List<Promise<FlakyTestReport>> candidates = new ArrayList<>();
    for (List<TestRunRecord> records : grouped.values()) {
      TestRunSummary summary = summarize(records);
      if (summary.failureRate() > FLAKINESS_THRESHOLD && summary.hasMixedResults()) {
        candidates.add(enrich(summary));
      }
    }

    if (candidates.isEmpty()) {
      return Promise.of(List.of());
    }

    return Promise.ofCallback(cb ->
        io.activej.promise.Promises.toList(candidates)
            .map(list -> list.stream().sorted(Comparator.comparingDouble(FlakyTestReport::flakinessScore).reversed()).toList())
            .subscribe(cb));
  }

  private Promise<FlakyTestReport> enrich(TestRunSummary summary) {
    FlakinessPattern pattern = classifyPattern(summary);
    String deterministicSuggestion = deterministicSuggestion(pattern);
    if (aiService == null) {
      return Promise.of(summary.toReport(pattern, deterministicSuggestion));
    }
    return aiService.reason(buildPrompt(summary, pattern))
        .map(suggestion -> summary.toReport(pattern, suggestion == null || suggestion.isBlank() ? deterministicSuggestion : suggestion));
  }

  private String buildPrompt(TestRunSummary summary, FlakinessPattern pattern) {
    return "Diagnose flaky test "
        + summary.testClass()
        + "#"
        + summary.testMethod()
        + " with pattern "
        + pattern
        + ". Failure rate="
        + summary.failureRate();
  }

  private String deterministicSuggestion(FlakinessPattern pattern) {
    return switch (pattern) {
      case TIME_DEPENDENT -> "Inject a controllable clock and remove wall-clock assumptions.";
      case ORDER_DEPENDENT -> "Reset shared state between tests and randomize test order locally.";
      case EXTERNAL_DEPENDENCY -> "Stub the external dependency and pin environment-specific inputs.";
      case RESOURCE_RACE -> "Serialize shared resource access or wait for asynchronous work to complete deterministically.";
    };
  }

  private TestRunSummary summarize(List<TestRunRecord> records) {
    int failures = 0;
    int passes = 0;
    double minDuration = Double.MAX_VALUE;
    double maxDuration = 0.0;
    boolean orderSensitiveFailure = false;
    boolean externalEnvVariance = false;
    Map<String, Boolean> environmentOutcomes = new HashMap<>();

    for (TestRunRecord record : records) {
      if (record.result() == TestResult.FAILED) {
        failures++;
        if (record.executionOrder() > 1) {
          orderSensitiveFailure = true;
        }
      } else {
        passes++;
      }
      minDuration = Math.min(minDuration, record.duration().toMillis());
      maxDuration = Math.max(maxDuration, record.duration().toMillis());
      if (record.environment() != null && !record.environment().isBlank()) {
        Boolean previous = environmentOutcomes.putIfAbsent(record.environment(), record.result() == TestResult.PASSED);
        if (previous != null && previous != (record.result() == TestResult.PASSED)) {
          externalEnvVariance = true;
        }
      }
    }

    return new TestRunSummary(
        records.getFirst().testClass(),
        records.getFirst().testMethod(),
        failures,
        passes,
        failures / (double) records.size(),
        maxDuration - minDuration > 500.0,
        orderSensitiveFailure,
        externalEnvVariance);
  }

  FlakinessPattern classifyPattern(TestRunSummary summary) {
    if (summary.timeVariance()) {
      return FlakinessPattern.TIME_DEPENDENT;
    }
    if (summary.orderDependent()) {
      return FlakinessPattern.ORDER_DEPENDENT;
    }
    if (summary.environmentDependent()) {
      return FlakinessPattern.EXTERNAL_DEPENDENCY;
    }
    return FlakinessPattern.RESOURCE_RACE;
  }

  public enum FlakinessPattern {
    TIME_DEPENDENT,
    ORDER_DEPENDENT,
    EXTERNAL_DEPENDENCY,
    RESOURCE_RACE
  }

  public enum TestResult {
    PASSED,
    FAILED
  }

  public record TestRunRecord(
      String testClass,
      String testMethod,
      TestResult result,
      Duration duration,
      int executionOrder,
      String environment,
      Instant ranAt) {

    public String testId() {
      return testClass + "#" + testMethod;
    }
  }

  record TestRunSummary(
      String testClass,
      String testMethod,
      int failures,
      int passes,
      double failureRate,
      boolean timeVariance,
      boolean orderDependent,
      boolean environmentDependent) {

    boolean hasMixedResults() {
      return failures > 0 && passes > 0;
    }

    FlakyTestReport toReport(FlakinessPattern pattern, String suggestion) {
      return new FlakyTestReport(testClass, testMethod, failureRate, pattern, suggestion);
    }
  }

  public record FlakyTestReport(
      String testClass,
      String testMethod,
      double flakinessScore,
      FlakinessPattern pattern,
      String fixSuggestion) {

    public String testId() {
      return testClass + "#" + testMethod;
    }
  }
}