package com.ghatana.yappc.agents.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@DisplayName("FlakyTestDetector Tests")
class FlakyTestDetectorTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  FlakyTestDetectorTest() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @DisplayName("detectFlakyTests classifies time dependent tests")
  void detectFlakyTestsClassifiesTimeDependentTests() {
    FlakyTestDetector detector = new FlakyTestDetector();

    List<FlakyTestDetector.FlakyTestReport> reports =
        runPromise(
            () ->
                detector.detectFlakyTests(
                    List.of(
                        record("ClockTest", "shouldTick", FlakyTestDetector.TestResult.PASSED, 10, 1, "ci"),
                        record("ClockTest", "shouldTick", FlakyTestDetector.TestResult.FAILED, 900, 1, "ci"))));

    assertThat(reports).hasSize(1);
    assertThat(reports.getFirst().pattern()).isEqualTo(FlakyTestDetector.FlakinessPattern.TIME_DEPENDENT);
    assertThat(reports.getFirst().fixSuggestion()).contains("clock");
  }

  @Test
  @DisplayName("detectFlakyTests classifies order dependent and environment dependent tests")
  void detectFlakyTestsClassifiesOrderAndEnvironmentDependentTests() {
    FlakyTestDetector detector = new FlakyTestDetector();

    List<FlakyTestDetector.FlakyTestReport> reports =
        runPromise(
            () ->
                detector.detectFlakyTests(
                    List.of(
                        record("SuiteTest", "failsWhenSecond", FlakyTestDetector.TestResult.PASSED, 100, 1, "ci"),
                        record("SuiteTest", "failsWhenSecond", FlakyTestDetector.TestResult.FAILED, 120, 2, "ci"),
                        record("ApiTest", "dependsOnEnv", FlakyTestDetector.TestResult.PASSED, 100, 1, "local"),
                        record("ApiTest", "dependsOnEnv", FlakyTestDetector.TestResult.FAILED, 100, 1, "local"),
                        record("RaceTest", "hasRace", FlakyTestDetector.TestResult.PASSED, 100, 1, "ci"),
                        record("RaceTest", "hasRace", FlakyTestDetector.TestResult.FAILED, 110, 1, "staging"))));

    assertThat(reports).hasSize(3);
    assertThat(reports).extracting(FlakyTestDetector.FlakyTestReport::pattern)
                .containsExactlyInAnyOrder(
            FlakyTestDetector.FlakinessPattern.ORDER_DEPENDENT,
            FlakyTestDetector.FlakinessPattern.EXTERNAL_DEPENDENCY,
            FlakyTestDetector.FlakinessPattern.RESOURCE_RACE);
  }

  @Test
  @DisplayName("detectFlakyTests uses AI suggestions when available")
  void detectFlakyTestsUsesAiSuggestionsWhenAvailable() {
    when(aiService.reason(anyString())).thenReturn(Promise.of("Use a stable fake backend."));

    FlakyTestDetector detector = new FlakyTestDetector(aiService);

    List<FlakyTestDetector.FlakyTestReport> reports =
        runPromise(
            () ->
                detector.detectFlakyTests(
                    List.of(
                        record("ExternalTest", "serviceCall", FlakyTestDetector.TestResult.PASSED, 50, 1, "dev"),
                        record("ExternalTest", "serviceCall", FlakyTestDetector.TestResult.FAILED, 50, 1, "dev"))));

    assertThat(reports.getFirst().fixSuggestion()).isEqualTo("Use a stable fake backend.");
  }

  @Test
  @DisplayName("detectFlakyTests ignores stable histories")
  void detectFlakyTestsIgnoresStableHistories() {
    FlakyTestDetector detector = new FlakyTestDetector();

    List<FlakyTestDetector.FlakyTestReport> reports =
        runPromise(
            () ->
                detector.detectFlakyTests(
                    List.of(
                        record("StableTest", "alwaysPasses", FlakyTestDetector.TestResult.PASSED, 100, 1, "ci"),
                        record("StableTest", "alwaysPasses", FlakyTestDetector.TestResult.PASSED, 100, 1, "ci"))));

    assertThat(reports).isEmpty();
  }

  @Test
  @DisplayName("detectFlakyTests handles null history and blank AI suggestions")
  void detectFlakyTestsHandlesNullHistoryAndBlankAiSuggestions() {
    when(aiService.reason(anyString())).thenReturn(Promise.of(" "));

    FlakyTestDetector detector = new FlakyTestDetector(aiService);

    assertThat(runPromise(() -> detector.detectFlakyTests(null))).isEmpty();

    List<FlakyTestDetector.FlakyTestReport> reports =
        runPromise(
            () ->
                detector.detectFlakyTests(
                    List.of(
                        new FlakyTestDetector.TestRunRecord(
                            "EnvTest",
                            "nullEnv",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(100),
                            1,
                            null,
                            Instant.parse("2026-04-06T00:00:00Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "EnvTest",
                            "nullEnv",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(100),
                            1,
                            null,
                            Instant.parse("2026-04-06T00:01:00Z")))));

    assertThat(reports.getFirst().fixSuggestion()).contains("Serialize shared resource access");
    assertThat(reports.getFirst().testId()).isEqualTo("EnvTest#nullEnv");
  }

  @Test
  @DisplayName("detectFlakyTests covers empty history null suggestions blank environments and summary helpers")
  void detectFlakyTestsCoversEmptyHistoryNullSuggestionsBlankEnvironmentsAndSummaryHelpers() {
    when(aiService.reason(anyString())).thenReturn(Promise.of(null));

    FlakyTestDetector detector = new FlakyTestDetector(aiService);

    assertThat(runPromise(() -> detector.detectFlakyTests(List.of()))).isEmpty();

    List<FlakyTestDetector.FlakyTestReport> reports =
        runPromise(
            () ->
                detector.detectFlakyTests(
                    List.of(
                        new FlakyTestDetector.TestRunRecord(
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:00:00Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:00Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:10Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:20Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:30Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:40Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:50Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:55Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:56Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:57Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:58Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:59Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:02:00Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:03:00Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:04:00Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:05:00Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:06:00Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:07:00Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:08:00Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:09:00Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:10:00Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:11:00Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "OnlyFailing",
                            "alwaysFails",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:12:00Z")),
                        new FlakyTestDetector.TestRunRecord(
                            "OnlyFailing",
                            "alwaysFails",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20),
                            1,
                            "",
                            Instant.parse("2026-04-06T02:13:00Z")))));

    assertThat(reports).hasSize(1);
    assertThat(reports.getFirst().fixSuggestion()).contains("Serialize shared resource access");
    assertThat(new FlakyTestDetector.TestRunSummary("OnlyFailures", "method", 3, 0, 1.0, false, false, false).hasMixedResults())
        .isFalse();
    assertThat(new FlakyTestDetector.TestRunSummary("OnlyPasses", "method", 0, 3, 0.0, false, false, false).hasMixedResults())
        .isFalse();
  }

  private FlakyTestDetector.TestRunRecord record(
      String testClass,
      String testMethod,
      FlakyTestDetector.TestResult result,
      long durationMillis,
      int executionOrder,
      String environment) {
    return new FlakyTestDetector.TestRunRecord(
        testClass,
        testMethod,
        result,
        Duration.ofMillis(durationMillis),
        executionOrder,
        environment,
        Instant.parse("2026-04-06T00:00:00Z"));
  }
}
