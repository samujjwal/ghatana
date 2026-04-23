package com.ghatana.yappc.platform.ai.analyzers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.CodeChangedEvent;
import io.activej.promise.Promise;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("TestGapAnalyzer Tests")
class TestGapAnalyzerTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("analyze skips Java test files")
  void analyzeSkipsJavaTestFiles() { // GH-90000
    TestGapAnalyzer analyzer = analyzer(healthyCoverage(), healthyHistory()); // GH-90000

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/test/java/com/acme/ExampleTest.java", "+assertThat(x)"))); // GH-90000

    assertThat(insights).isEmpty(); // GH-90000
    verifyNoInteractions(aiService); // GH-90000
  }

  @Test
  @DisplayName("analyze skips TypeScript test file variants")
  void analyzeSkipsTypescriptTestFileVariants() { // GH-90000
    TestGapAnalyzer analyzer = analyzer(healthyCoverage(), healthyHistory()); // GH-90000

    assertThat( // GH-90000
            runPromise(() -> analyzer.analyze(event("frontend/src/foo/__tests__/bar.ts", "+test")))) // GH-90000
        .isEmpty(); // GH-90000
    assertThat( // GH-90000
            runPromise(() -> analyzer.analyze(event("frontend/src/foo/bar.test.ts", "+test")))) // GH-90000
        .isEmpty(); // GH-90000
    assertThat( // GH-90000
            runPromise(() -> analyzer.analyze(event("frontend/src/foo/bar.test.tsx", "+test")))) // GH-90000
        .isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("analyze returns deterministic findings for coverage and stability gaps")
  void analyzeReturnsDeterministicFindingsForCoverageAndStabilityGaps() { // GH-90000
    TestGapAnalyzer analyzer =
        analyzer( // GH-90000
            new TestGapAnalyzer.CoverageSnapshot(0.62, 0.5, 9, 3, List.of("OrderService#submit")),
            new TestGapAnalyzer.RecentTestHistory(List.of(), 2, 1, 4)); // GH-90000

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/main/java/com/acme/OrderService.java", "+if (ready) {}"))); // GH-90000

    assertThat(insights).hasSize(3); // GH-90000
    assertThat(insights).extracting(AIInsight::title) // GH-90000
        .contains("No related tests found", "Coverage gap detected", "Existing tests are unstable"); // GH-90000
    assertThat(insights).allMatch(insight -> insight.type() == AIInsight.InsightType.TEST_GAP); // GH-90000
    verifyNoInteractions(aiService); // GH-90000
  }

  @Test
  @DisplayName("analyze flags alternate coverage and flaky-only deterministic paths")
  void analyzeFlagsAlternateCoverageAndFlakyOnlyDeterministicPaths() { // GH-90000
    TestGapAnalyzer analyzer =
        analyzer( // GH-90000
            new TestGapAnalyzer.CoverageSnapshot(0.95, 0.7, 1, 1, List.of("OrderService#submit")),
            new TestGapAnalyzer.RecentTestHistory(List.of("OrderServiceTest"), 0, 1, 2));

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/main/java/com/acme/OrderService.java", "+if (ready) {}"))); // GH-90000

    assertThat(insights).hasSize(2); // GH-90000
    assertThat(insights).extracting(AIInsight::title) // GH-90000
        .contains("Coverage gap detected", "Existing tests are unstable"); // GH-90000
  }

  @Test
  @DisplayName("analyze flags missed lines even when aggregate coverage percentages are healthy")
  void analyzeFlagsMissedLinesWhenCoveragePercentagesAreHealthy() { // GH-90000
    TestGapAnalyzer analyzer =
        analyzer( // GH-90000
            new TestGapAnalyzer.CoverageSnapshot(0.95, 0.92, 2, 0, List.of("OrderService#submit")),
            healthyHistory()); // GH-90000

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/main/java/com/acme/OrderService.java", "+line"))); // GH-90000

    assertThat(insights).singleElement().satisfies(insight -> { // GH-90000
      assertThat(insight.title()).isEqualTo("Coverage gap detected");
      assertThat(insight.type()).isEqualTo(AIInsight.InsightType.TEST_GAP); // GH-90000
    });
  }

  @Test
  @DisplayName("analyze flags missed branches even when missed lines are zero")
  void analyzeFlagsMissedBranchesWhenMissedLinesAreZero() { // GH-90000
    TestGapAnalyzer analyzer =
        analyzer( // GH-90000
            new TestGapAnalyzer.CoverageSnapshot(0.95, 0.92, 0, 2, List.of("OrderService#submit")),
            healthyHistory()); // GH-90000

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/main/java/com/acme/OrderService.java", "+branch"))); // GH-90000

    assertThat(insights).singleElement().satisfies(insight -> { // GH-90000
      assertThat(insight.title()).isEqualTo("Coverage gap detected");
      assertThat(insight.type()).isEqualTo(AIInsight.InsightType.TEST_GAP); // GH-90000
    });
  }

  @Test
  @DisplayName("analyze parses structured AI test gap suggestions")
  void analyzeParsesStructuredAiTestGapSuggestions() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn( // GH-90000
            Promise.of( // GH-90000
                "[{\"severity\":\"warning\",\"uncoveredPath\":\"OrderService#submit unhappy path\",\"description\":\"Branch remains uncovered\",\"suggestedTest\":\"Add a rollback regression test\",\"lineNumber\":\"17\",\"confidence\":\"0.84\"}]"));

    TestGapAnalyzer analyzer = analyzer(healthyCoverage(), healthyHistory()); // GH-90000

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/main/java/com/acme/OrderService.java", "+service.submit();"))); // GH-90000

    assertThat(insights).hasSize(1); // GH-90000
    assertThat(insights.getFirst().title()).isEqualTo("OrderService#submit unhappy path");
    assertThat(insights.getFirst().lineNumber()).isEqualTo(17); // GH-90000
    assertThat(insights.getFirst().confidence()).isEqualTo(0.84); // GH-90000
  }

  @Test
  @DisplayName("analyze defaults missing AI numeric fields")
  void analyzeDefaultsMissingAiNumericFields() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of("[{\"severity\":\"info\",\"uncoveredPath\":\"InvoiceService#publish\"}]")); // GH-90000

    TestGapAnalyzer analyzer = analyzer(healthyCoverage(), healthyHistory()); // GH-90000

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/main/java/com/acme/InvoiceService.java", "+publish();"))); // GH-90000

    assertThat(insights).singleElement().satisfies(insight -> { // GH-90000
      assertThat(insight.lineNumber()).isZero(); // GH-90000
      assertThat(insight.confidence()).isZero(); // GH-90000
      assertThat(insight.severity()).isEqualTo(AIInsight.InsightSeverity.INFO); // GH-90000
    });
  }

  @Test
  @DisplayName("analyze accepts numeric AI line and confidence values")
  void analyzeAcceptsNumericAiLineAndConfidenceValues() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn( // GH-90000
            Promise.of( // GH-90000
                "[{\"severity\":\"warning\",\"uncoveredPath\":\"BillingService#collect\",\"lineNumber\":12,\"confidence\":0.66}]"));

    TestGapAnalyzer analyzer = analyzer(healthyCoverage(), healthyHistory()); // GH-90000

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/main/java/com/acme/BillingService.java", "+collect();"))); // GH-90000

    assertThat(insights).singleElement().satisfies(insight -> { // GH-90000
      assertThat(insight.lineNumber()).isEqualTo(12); // GH-90000
      assertThat(insight.confidence()).isEqualTo(0.66); // GH-90000
    });
  }

  @Test
  @DisplayName("analyze returns empty list for blank and null AI responses")
  void analyzeReturnsEmptyListForBlankAndNullAiResponses() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of(" "))
        .thenReturn(Promise.of(null)); // GH-90000

    TestGapAnalyzer analyzer = analyzer(healthyCoverage(), healthyHistory()); // GH-90000

    assertThat( // GH-90000
            runPromise( // GH-90000
                () -> analyzer.analyze(event("src/main/java/com/acme/ShippingService.java", "+ship();")))) // GH-90000
        .isEmpty(); // GH-90000
    assertThat( // GH-90000
            runPromise( // GH-90000
                () -> analyzer.analyze(event("src/main/java/com/acme/BillingService.java", "+bill();")))) // GH-90000
        .isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("analyze falls back to manual review insight for malformed AI response")
  void analyzeFallsBackToManualReviewInsightForMalformedAiResponse() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("not-json"));

    TestGapAnalyzer analyzer = analyzer(healthyCoverage(), healthyHistory()); // GH-90000

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/main/java/com/acme/ReviewService.java", "+review();"))); // GH-90000

    assertThat(insights).hasSize(1); // GH-90000
    assertThat(insights.getFirst().title()).isEqualTo("Manual test gap review recommended");
  }

  @Test
  @DisplayName("coverage and history records normalize invalid values")
  void recordsNormalizeInvalidValues() { // GH-90000
    TestGapAnalyzer.CoverageSnapshot coverage =
        new TestGapAnalyzer.CoverageSnapshot(1.5, -1.0, -2, -3, null); // GH-90000
    TestGapAnalyzer.RecentTestHistory history =
        new TestGapAnalyzer.RecentTestHistory(null, -1, -2, -3); // GH-90000

    assertThat(coverage.lineCoverage()).isEqualTo(1.0); // GH-90000
    assertThat(coverage.branchCoverage()).isZero(); // GH-90000
    assertThat(coverage.missedLines()).isZero(); // GH-90000
    assertThat(coverage.uncoveredPaths()).isEmpty(); // GH-90000
    assertThat(coverage.summary()).contains("100%", "0%", "missedLines=0", "missedBranches=0"); // GH-90000

    assertThat(history.relatedTestFiles()).isEmpty(); // GH-90000
    assertThat(history.recentFailures()).isZero(); // GH-90000
    assertThat(history.summary()).contains("recentFailures=0", "flakyTests=0", "lastRunAgeDays=0"); // GH-90000
  }

  private TestGapAnalyzer analyzer( // GH-90000
      TestGapAnalyzer.CoverageSnapshot coverage, TestGapAnalyzer.RecentTestHistory history) {
    return new TestGapAnalyzer( // GH-90000
        aiService,
        (projectId, filePath) -> Promise.of(coverage), // GH-90000
        (projectId, filePath) -> Promise.of(history)); // GH-90000
  }

  private TestGapAnalyzer.CoverageSnapshot healthyCoverage() { // GH-90000
    return new TestGapAnalyzer.CoverageSnapshot(0.95, 0.92, 0, 0, List.of("OrderService#submit"));
  }

  private TestGapAnalyzer.RecentTestHistory healthyHistory() { // GH-90000
    return new TestGapAnalyzer.RecentTestHistory(List.of("OrderServiceTest"), 0, 0, 1);
  }

  private CodeChangedEvent event(String filePath, String diff) { // GH-90000
    return new CodeChangedEvent("tenant-a", "project-a", filePath, diff); // GH-90000
  }
}
