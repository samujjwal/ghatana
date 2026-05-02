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

@ExtendWith(MockitoExtension.class) 
@DisplayName("TestGapAnalyzer Tests")
class TestGapAnalyzerTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("analyze skips Java test files")
  void analyzeSkipsJavaTestFiles() { 
    TestGapAnalyzer analyzer = analyzer(healthyCoverage(), healthyHistory()); 

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/test/java/com/acme/ExampleTest.java", "+assertThat(x)"))); 

    assertThat(insights).isEmpty(); 
    verifyNoInteractions(aiService); 
  }

  @Test
  @DisplayName("analyze skips TypeScript test file variants")
  void analyzeSkipsTypescriptTestFileVariants() { 
    TestGapAnalyzer analyzer = analyzer(healthyCoverage(), healthyHistory()); 

    assertThat( 
            runPromise(() -> analyzer.analyze(event("frontend/src/foo/__tests__/bar.ts", "+test")))) 
        .isEmpty(); 
    assertThat( 
            runPromise(() -> analyzer.analyze(event("frontend/src/foo/bar.test.ts", "+test")))) 
        .isEmpty(); 
    assertThat( 
            runPromise(() -> analyzer.analyze(event("frontend/src/foo/bar.test.tsx", "+test")))) 
        .isEmpty(); 
  }

  @Test
  @DisplayName("analyze returns deterministic findings for coverage and stability gaps")
  void analyzeReturnsDeterministicFindingsForCoverageAndStabilityGaps() { 
    TestGapAnalyzer analyzer =
        analyzer( 
            new TestGapAnalyzer.CoverageSnapshot(0.62, 0.5, 9, 3, List.of("OrderService#submit")),
            new TestGapAnalyzer.RecentTestHistory(List.of(), 2, 1, 4)); 

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/main/java/com/acme/OrderService.java", "+if (ready) {}"))); 

    assertThat(insights).hasSize(3); 
    assertThat(insights).extracting(AIInsight::title) 
        .contains("No related tests found", "Coverage gap detected", "Existing tests are unstable"); 
    assertThat(insights).allMatch(insight -> insight.type() == AIInsight.InsightType.TEST_GAP); 
    verifyNoInteractions(aiService); 
  }

  @Test
  @DisplayName("analyze flags alternate coverage and flaky-only deterministic paths")
  void analyzeFlagsAlternateCoverageAndFlakyOnlyDeterministicPaths() { 
    TestGapAnalyzer analyzer =
        analyzer( 
            new TestGapAnalyzer.CoverageSnapshot(0.95, 0.7, 1, 1, List.of("OrderService#submit")),
            new TestGapAnalyzer.RecentTestHistory(List.of("OrderServiceTest"), 0, 1, 2));

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/main/java/com/acme/OrderService.java", "+if (ready) {}"))); 

    assertThat(insights).hasSize(2); 
    assertThat(insights).extracting(AIInsight::title) 
        .contains("Coverage gap detected", "Existing tests are unstable"); 
  }

  @Test
  @DisplayName("analyze flags missed lines even when aggregate coverage percentages are healthy")
  void analyzeFlagsMissedLinesWhenCoveragePercentagesAreHealthy() { 
    TestGapAnalyzer analyzer =
        analyzer( 
            new TestGapAnalyzer.CoverageSnapshot(0.95, 0.92, 2, 0, List.of("OrderService#submit")),
            healthyHistory()); 

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/main/java/com/acme/OrderService.java", "+line"))); 

    assertThat(insights).singleElement().satisfies(insight -> { 
      assertThat(insight.title()).isEqualTo("Coverage gap detected");
      assertThat(insight.type()).isEqualTo(AIInsight.InsightType.TEST_GAP); 
    });
  }

  @Test
  @DisplayName("analyze flags missed branches even when missed lines are zero")
  void analyzeFlagsMissedBranchesWhenMissedLinesAreZero() { 
    TestGapAnalyzer analyzer =
        analyzer( 
            new TestGapAnalyzer.CoverageSnapshot(0.95, 0.92, 0, 2, List.of("OrderService#submit")),
            healthyHistory()); 

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/main/java/com/acme/OrderService.java", "+branch"))); 

    assertThat(insights).singleElement().satisfies(insight -> { 
      assertThat(insight.title()).isEqualTo("Coverage gap detected");
      assertThat(insight.type()).isEqualTo(AIInsight.InsightType.TEST_GAP); 
    });
  }

  @Test
  @DisplayName("analyze parses structured AI test gap suggestions")
  void analyzeParsesStructuredAiTestGapSuggestions() { 
    when(aiService.reason(anyString(), anyMap())) 
        .thenReturn( 
            Promise.of( 
                "[{\"severity\":\"warning\",\"uncoveredPath\":\"OrderService#submit unhappy path\",\"description\":\"Branch remains uncovered\",\"suggestedTest\":\"Add a rollback regression test\",\"lineNumber\":\"17\",\"confidence\":\"0.84\"}]"));

    TestGapAnalyzer analyzer = analyzer(healthyCoverage(), healthyHistory()); 

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/main/java/com/acme/OrderService.java", "+service.submit();"))); 

    assertThat(insights).hasSize(1); 
    assertThat(insights.getFirst().title()).isEqualTo("OrderService#submit unhappy path");
    assertThat(insights.getFirst().lineNumber()).isEqualTo(17); 
    assertThat(insights.getFirst().confidence()).isEqualTo(0.84); 
  }

  @Test
  @DisplayName("analyze defaults missing AI numeric fields")
  void analyzeDefaultsMissingAiNumericFields() { 
    when(aiService.reason(anyString(), anyMap())) 
        .thenReturn(Promise.of("[{\"severity\":\"info\",\"uncoveredPath\":\"InvoiceService#publish\"}]")); 

    TestGapAnalyzer analyzer = analyzer(healthyCoverage(), healthyHistory()); 

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/main/java/com/acme/InvoiceService.java", "+publish();"))); 

    assertThat(insights).singleElement().satisfies(insight -> { 
      assertThat(insight.lineNumber()).isZero(); 
      assertThat(insight.confidence()).isZero(); 
      assertThat(insight.severity()).isEqualTo(AIInsight.InsightSeverity.INFO); 
    });
  }

  @Test
  @DisplayName("analyze accepts numeric AI line and confidence values")
  void analyzeAcceptsNumericAiLineAndConfidenceValues() { 
    when(aiService.reason(anyString(), anyMap())) 
        .thenReturn( 
            Promise.of( 
                "[{\"severity\":\"warning\",\"uncoveredPath\":\"BillingService#collect\",\"lineNumber\":12,\"confidence\":0.66}]"));

    TestGapAnalyzer analyzer = analyzer(healthyCoverage(), healthyHistory()); 

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/main/java/com/acme/BillingService.java", "+collect();"))); 

    assertThat(insights).singleElement().satisfies(insight -> { 
      assertThat(insight.lineNumber()).isEqualTo(12); 
      assertThat(insight.confidence()).isEqualTo(0.66); 
    });
  }

  @Test
  @DisplayName("analyze returns empty list for blank and null AI responses")
  void analyzeReturnsEmptyListForBlankAndNullAiResponses() { 
    when(aiService.reason(anyString(), anyMap())) 
        .thenReturn(Promise.of(" "))
        .thenReturn(Promise.of(null)); 

    TestGapAnalyzer analyzer = analyzer(healthyCoverage(), healthyHistory()); 

    assertThat( 
            runPromise( 
                () -> analyzer.analyze(event("src/main/java/com/acme/ShippingService.java", "+ship();")))) 
        .isEmpty(); 
    assertThat( 
            runPromise( 
                () -> analyzer.analyze(event("src/main/java/com/acme/BillingService.java", "+bill();")))) 
        .isEmpty(); 
  }

  @Test
  @DisplayName("analyze falls back to manual review insight for malformed AI response")
  void analyzeFallsBackToManualReviewInsightForMalformedAiResponse() { 
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("not-json"));

    TestGapAnalyzer analyzer = analyzer(healthyCoverage(), healthyHistory()); 

    List<AIInsight> insights =
        runPromise(() -> analyzer.analyze(event("src/main/java/com/acme/ReviewService.java", "+review();"))); 

    assertThat(insights).hasSize(1); 
    assertThat(insights.getFirst().title()).isEqualTo("Manual test gap review recommended");
  }

  @Test
  @DisplayName("coverage and history records normalize invalid values")
  void recordsNormalizeInvalidValues() { 
    TestGapAnalyzer.CoverageSnapshot coverage =
        new TestGapAnalyzer.CoverageSnapshot(1.5, -1.0, -2, -3, null); 
    TestGapAnalyzer.RecentTestHistory history =
        new TestGapAnalyzer.RecentTestHistory(null, -1, -2, -3); 

    assertThat(coverage.lineCoverage()).isEqualTo(1.0); 
    assertThat(coverage.branchCoverage()).isZero(); 
    assertThat(coverage.missedLines()).isZero(); 
    assertThat(coverage.uncoveredPaths()).isEmpty(); 
    assertThat(coverage.summary()).contains("100%", "0%", "missedLines=0", "missedBranches=0"); 

    assertThat(history.relatedTestFiles()).isEmpty(); 
    assertThat(history.recentFailures()).isZero(); 
    assertThat(history.summary()).contains("recentFailures=0", "flakyTests=0", "lastRunAgeDays=0"); 
  }

  private TestGapAnalyzer analyzer( 
      TestGapAnalyzer.CoverageSnapshot coverage, TestGapAnalyzer.RecentTestHistory history) {
    return new TestGapAnalyzer( 
        aiService,
        (projectId, filePath) -> Promise.of(coverage), 
        (projectId, filePath) -> Promise.of(history)); 
  }

  private TestGapAnalyzer.CoverageSnapshot healthyCoverage() { 
    return new TestGapAnalyzer.CoverageSnapshot(0.95, 0.92, 0, 0, List.of("OrderService#submit"));
  }

  private TestGapAnalyzer.RecentTestHistory healthyHistory() { 
    return new TestGapAnalyzer.RecentTestHistory(List.of("OrderServiceTest"), 0, 0, 1);
  }

  private CodeChangedEvent event(String filePath, String diff) { 
    return new CodeChangedEvent("tenant-a", "project-a", filePath, diff); 
  }
}
