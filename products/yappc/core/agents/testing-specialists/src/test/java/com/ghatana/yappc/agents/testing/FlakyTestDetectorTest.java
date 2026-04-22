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

@DisplayName("FlakyTestDetector Tests [GH-90000]")
class FlakyTestDetectorTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  FlakyTestDetectorTest() { // GH-90000
    MockitoAnnotations.openMocks(this); // GH-90000
  }

  @Test
  @DisplayName("detectFlakyTests classifies time dependent tests [GH-90000]")
  void detectFlakyTestsClassifiesTimeDependentTests() { // GH-90000
    FlakyTestDetector detector = new FlakyTestDetector(); // GH-90000

    List<FlakyTestDetector.FlakyTestReport> reports =
        runPromise( // GH-90000
            () -> // GH-90000
                detector.detectFlakyTests( // GH-90000
                    List.of( // GH-90000
                        record("ClockTest", "shouldTick", FlakyTestDetector.TestResult.PASSED, 10, 1, "ci"), // GH-90000
                        record("ClockTest", "shouldTick", FlakyTestDetector.TestResult.FAILED, 900, 1, "ci")))); // GH-90000

    assertThat(reports).hasSize(1); // GH-90000
    assertThat(reports.getFirst().pattern()).isEqualTo(FlakyTestDetector.FlakinessPattern.TIME_DEPENDENT); // GH-90000
    assertThat(reports.getFirst().fixSuggestion()).contains("clock [GH-90000]");
  }

  @Test
  @DisplayName("detectFlakyTests classifies order dependent and environment dependent tests [GH-90000]")
  void detectFlakyTestsClassifiesOrderAndEnvironmentDependentTests() { // GH-90000
    FlakyTestDetector detector = new FlakyTestDetector(); // GH-90000

    List<FlakyTestDetector.FlakyTestReport> reports =
        runPromise( // GH-90000
            () -> // GH-90000
                detector.detectFlakyTests( // GH-90000
                    List.of( // GH-90000
                        record("SuiteTest", "failsWhenSecond", FlakyTestDetector.TestResult.PASSED, 100, 1, "ci"), // GH-90000
                        record("SuiteTest", "failsWhenSecond", FlakyTestDetector.TestResult.FAILED, 120, 2, "ci"), // GH-90000
                        record("ApiTest", "dependsOnEnv", FlakyTestDetector.TestResult.PASSED, 100, 1, "local"), // GH-90000
                        record("ApiTest", "dependsOnEnv", FlakyTestDetector.TestResult.FAILED, 100, 1, "local"), // GH-90000
                        record("RaceTest", "hasRace", FlakyTestDetector.TestResult.PASSED, 100, 1, "ci"), // GH-90000
                        record("RaceTest", "hasRace", FlakyTestDetector.TestResult.FAILED, 110, 1, "staging")))); // GH-90000

    assertThat(reports).hasSize(3); // GH-90000
    assertThat(reports).extracting(FlakyTestDetector.FlakyTestReport::pattern) // GH-90000
                .containsExactlyInAnyOrder( // GH-90000
            FlakyTestDetector.FlakinessPattern.ORDER_DEPENDENT,
            FlakyTestDetector.FlakinessPattern.EXTERNAL_DEPENDENCY,
            FlakyTestDetector.FlakinessPattern.RESOURCE_RACE);
  }

  @Test
  @DisplayName("detectFlakyTests uses AI suggestions when available [GH-90000]")
  void detectFlakyTestsUsesAiSuggestionsWhenAvailable() { // GH-90000
    when(aiService.reason(anyString())).thenReturn(Promise.of("Use a stable fake backend. [GH-90000]"));

    FlakyTestDetector detector = new FlakyTestDetector(aiService); // GH-90000

    List<FlakyTestDetector.FlakyTestReport> reports =
        runPromise( // GH-90000
            () -> // GH-90000
                detector.detectFlakyTests( // GH-90000
                    List.of( // GH-90000
                        record("ExternalTest", "serviceCall", FlakyTestDetector.TestResult.PASSED, 50, 1, "dev"), // GH-90000
                        record("ExternalTest", "serviceCall", FlakyTestDetector.TestResult.FAILED, 50, 1, "dev")))); // GH-90000

    assertThat(reports.getFirst().fixSuggestion()).isEqualTo("Use a stable fake backend. [GH-90000]");
  }

  @Test
  @DisplayName("detectFlakyTests ignores stable histories [GH-90000]")
  void detectFlakyTestsIgnoresStableHistories() { // GH-90000
    FlakyTestDetector detector = new FlakyTestDetector(); // GH-90000

    List<FlakyTestDetector.FlakyTestReport> reports =
        runPromise( // GH-90000
            () -> // GH-90000
                detector.detectFlakyTests( // GH-90000
                    List.of( // GH-90000
                        record("StableTest", "alwaysPasses", FlakyTestDetector.TestResult.PASSED, 100, 1, "ci"), // GH-90000
                        record("StableTest", "alwaysPasses", FlakyTestDetector.TestResult.PASSED, 100, 1, "ci")))); // GH-90000

    assertThat(reports).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("detectFlakyTests handles null history and blank AI suggestions [GH-90000]")
  void detectFlakyTestsHandlesNullHistoryAndBlankAiSuggestions() { // GH-90000
    when(aiService.reason(anyString())).thenReturn(Promise.of("  [GH-90000]"));

    FlakyTestDetector detector = new FlakyTestDetector(aiService); // GH-90000

    assertThat(runPromise(() -> detector.detectFlakyTests(null))).isEmpty(); // GH-90000

    List<FlakyTestDetector.FlakyTestReport> reports =
        runPromise( // GH-90000
            () -> // GH-90000
                detector.detectFlakyTests( // GH-90000
                    List.of( // GH-90000
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "EnvTest",
                            "nullEnv",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(100), // GH-90000
                            1,
                            null,
                            Instant.parse("2026-04-06T00:00:00Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "EnvTest",
                            "nullEnv",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(100), // GH-90000
                            1,
                            null,
                            Instant.parse("2026-04-06T00:01:00Z [GH-90000]")))));

    assertThat(reports.getFirst().fixSuggestion()).contains("Serialize shared resource access [GH-90000]");
    assertThat(reports.getFirst().testId()).isEqualTo("EnvTest#nullEnv [GH-90000]");
  }

  @Test
  @DisplayName("detectFlakyTests covers empty history null suggestions blank environments and summary helpers [GH-90000]")
  void detectFlakyTestsCoversEmptyHistoryNullSuggestionsBlankEnvironmentsAndSummaryHelpers() { // GH-90000
    when(aiService.reason(anyString())).thenReturn(Promise.of(null)); // GH-90000

    FlakyTestDetector detector = new FlakyTestDetector(aiService); // GH-90000

    assertThat(runPromise(() -> detector.detectFlakyTests(List.of()))).isEmpty(); // GH-90000

    List<FlakyTestDetector.FlakyTestReport> reports =
        runPromise( // GH-90000
            () -> // GH-90000
                detector.detectFlakyTests( // GH-90000
                    List.of( // GH-90000
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:00:00Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:00Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:10Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:20Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:30Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:40Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:50Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:55Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:56Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:57Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:58Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "Borderline",
                            "mixed",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:01:59Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.PASSED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:02:00Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:03:00Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:04:00Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:05:00Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:06:00Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:07:00Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:08:00Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:09:00Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:10:00Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "MostlyFailing",
                            "boundary",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:11:00Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "OnlyFailing",
                            "alwaysFails",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:12:00Z [GH-90000]")),
                        new FlakyTestDetector.TestRunRecord( // GH-90000
                            "OnlyFailing",
                            "alwaysFails",
                            FlakyTestDetector.TestResult.FAILED,
                            Duration.ofMillis(20), // GH-90000
                            1,
                            "",
                            Instant.parse("2026-04-06T02:13:00Z [GH-90000]")))));

    assertThat(reports).hasSize(1); // GH-90000
    assertThat(reports.getFirst().fixSuggestion()).contains("Serialize shared resource access [GH-90000]");
    assertThat(new FlakyTestDetector.TestRunSummary("OnlyFailures", "method", 3, 0, 1.0, false, false, false).hasMixedResults()) // GH-90000
        .isFalse(); // GH-90000
    assertThat(new FlakyTestDetector.TestRunSummary("OnlyPasses", "method", 0, 3, 0.0, false, false, false).hasMixedResults()) // GH-90000
        .isFalse(); // GH-90000
  }

  private FlakyTestDetector.TestRunRecord record( // GH-90000
      String testClass,
      String testMethod,
      FlakyTestDetector.TestResult result,
      long durationMillis,
      int executionOrder,
      String environment) {
    return new FlakyTestDetector.TestRunRecord( // GH-90000
        testClass,
        testMethod,
        result,
        Duration.ofMillis(durationMillis), // GH-90000
        executionOrder,
        environment,
        Instant.parse("2026-04-06T00:00:00Z [GH-90000]"));
  }
}
