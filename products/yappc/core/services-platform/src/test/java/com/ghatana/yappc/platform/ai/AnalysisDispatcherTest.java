package com.ghatana.yappc.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.platform.ai.analyzers.ArchitectureAdvisor;
import com.ghatana.yappc.platform.ai.analyzers.CodeQualityAnalyzer;
import com.ghatana.yappc.platform.ai.analyzers.PerformanceAdvisor;
import com.ghatana.yappc.platform.ai.analyzers.RequirementsConsistencyChecker;
import com.ghatana.yappc.platform.ai.analyzers.SecurityPatternDetector;
import com.ghatana.yappc.platform.ai.analyzers.TestGapAnalyzer;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.ArchitectureChangedEvent;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.CodeChangedEvent;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.RequirementChangedEvent;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AnalysisDispatcher Tests [GH-90000]")
class AnalysisDispatcherTest extends EventloopTestBase {

  @Mock private CodeQualityAnalyzer codeQualityAnalyzer;
  @Mock private SecurityPatternDetector securityPatternDetector;
  @Mock private TestGapAnalyzer testGapAnalyzer;
  @Mock private PerformanceAdvisor performanceAdvisor;
  @Mock private RequirementsConsistencyChecker requirementsConsistencyChecker;
  @Mock private ArchitectureAdvisor architectureAdvisor;

  @Test
  @DisplayName("dispatch fans out code events across code, security, test, and performance analyzers [GH-90000]")
  void dispatchFansOutCodeEventsAcrossCodeSecurityTestAndPerformanceAnalyzers() { // GH-90000
    CodeChangedEvent event =
        new CodeChangedEvent("tenant-a", "project-a", "src/App.ts", "+const answer = 42;"); // GH-90000
    when(codeQualityAnalyzer.analyze(event)).thenReturn(Promise.of(List.of(insight("code [GH-90000]"))));
    when(securityPatternDetector.analyze(event)).thenReturn(Promise.of(List.of(insight("security [GH-90000]"))));
    when(testGapAnalyzer.analyze(event)).thenReturn(Promise.of(List.of(insight("tests [GH-90000]"))));
    when(performanceAdvisor.analyze(event)).thenReturn(Promise.of(List.of(insight("performance [GH-90000]"))));

    AnalysisDispatcher dispatcher = buildDispatcher(); // GH-90000
    List<AIInsight> insights = runPromise(() -> dispatcher.dispatch(event)); // GH-90000

    assertThat(insights) // GH-90000
      .extracting(AIInsight::title) // GH-90000
      .containsExactlyInAnyOrder("code", "security", "tests", "performance"); // GH-90000
    verify(codeQualityAnalyzer).analyze(event); // GH-90000
    verify(securityPatternDetector).analyze(event); // GH-90000
    verify(testGapAnalyzer).analyze(event); // GH-90000
    verify(performanceAdvisor).analyze(event); // GH-90000
    verifyNoInteractions(requirementsConsistencyChecker, architectureAdvisor); // GH-90000
    assertThat(event.correlationKey()).isEqualTo("tenant-a:project-a:src/App.ts [GH-90000]");
    assertThat(event.sourceRef()).isEqualTo("src/App.ts [GH-90000]");
  }

  @Test
  @DisplayName("dispatch routes requirement events to consistency checker [GH-90000]")
  void dispatchRoutesRequirementEventsToConsistencyChecker() { // GH-90000
    RequirementChangedEvent event =
        new RequirementChangedEvent( // GH-90000
            "tenant-b",
            "project-b",
            "REQ-42",
            "Offline mode",
            "The platform must support offline editing with sync recovery and acceptance criteria.",
            List.of("Legacy requirement [GH-90000]"));
    when(requirementsConsistencyChecker.analyze(event)) // GH-90000
        .thenReturn(Promise.of(List.of(insight("requirement [GH-90000]"))));

    AnalysisDispatcher dispatcher = buildDispatcher(); // GH-90000
    List<AIInsight> insights = runPromise(() -> dispatcher.dispatch(event)); // GH-90000

    assertThat(insights).singleElement().satisfies(insight -> assertThat(insight.title()).isEqualTo("requirement [GH-90000]"));
    verify(requirementsConsistencyChecker).analyze(event); // GH-90000
    verifyNoInteractions( // GH-90000
      codeQualityAnalyzer, securityPatternDetector, testGapAnalyzer, performanceAdvisor, architectureAdvisor);
    assertThat(event.correlationKey()).isEqualTo("tenant-b:project-b:requirement:REQ-42 [GH-90000]");
    assertThat(event.sourceRef()).isEqualTo("REQ-42 [GH-90000]");
  }

  @Test
  @DisplayName("dispatch routes architecture events to architecture advisor and normalizes null values [GH-90000]")
  void dispatchRoutesArchitectureEventsToArchitectureAdvisorAndNormalizesNullValues() { // GH-90000
    ArchitectureChangedEvent event =
        new ArchitectureChangedEvent(null, null, null, null, null, true); // GH-90000
    when(architectureAdvisor.analyze(event)).thenReturn(Promise.of(List.of(insight("architecture [GH-90000]"))));

    AnalysisDispatcher dispatcher = buildDispatcher(); // GH-90000
    List<AIInsight> insights = runPromise(() -> dispatcher.dispatch(event)); // GH-90000

    assertThat(insights).singleElement().satisfies(insight -> assertThat(insight.title()).isEqualTo("architecture [GH-90000]"));
    verify(architectureAdvisor).analyze(event); // GH-90000
    verifyNoInteractions( // GH-90000
      codeQualityAnalyzer,
      securityPatternDetector,
      testGapAnalyzer,
      performanceAdvisor,
      requirementsConsistencyChecker);
    assertThat(event.tenantId()).isEqualTo("unknown-tenant [GH-90000]");
    assertThat(event.projectId()).isEqualTo("unknown-project [GH-90000]");
    assertThat(event.componentName()).isEqualTo("unknown-component [GH-90000]");
    assertThat(event.changeSummary()).isEmpty(); // GH-90000
    assertThat(event.affectedModules()).isEmpty(); // GH-90000
    assertThat(event.correlationKey()).isEqualTo("unknown-tenant:unknown-project:architecture:unknown-component [GH-90000]");
    assertThat(event.sourceRef()).isEqualTo("unknown-component [GH-90000]");
  }

  private AnalysisDispatcher buildDispatcher() { // GH-90000
    return new AnalysisDispatcher( // GH-90000
        codeQualityAnalyzer,
        securityPatternDetector,
        testGapAnalyzer,
      performanceAdvisor,
        requirementsConsistencyChecker,
        architectureAdvisor);
  }

  private AIInsight insight(String title) { // GH-90000
    return new AIInsight( // GH-90000
        title + "-id",
        "tenant-a",
        "project-a",
        AIInsight.InsightType.CODE_QUALITY,
        AIInsight.InsightSeverity.INFO,
        title,
        "description",
        "suggestion",
        0.5,
        "source",
        0,
        List.of("test [GH-90000]"),
        Instant.EPOCH,
        false);
  }
}
