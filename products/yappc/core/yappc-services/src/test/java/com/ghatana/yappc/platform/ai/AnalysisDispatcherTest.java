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

@ExtendWith(MockitoExtension.class) 
@DisplayName("AnalysisDispatcher Tests")
class AnalysisDispatcherTest extends EventloopTestBase {

  @Mock private CodeQualityAnalyzer codeQualityAnalyzer;
  @Mock private SecurityPatternDetector securityPatternDetector;
  @Mock private TestGapAnalyzer testGapAnalyzer;
  @Mock private PerformanceAdvisor performanceAdvisor;
  @Mock private RequirementsConsistencyChecker requirementsConsistencyChecker;
  @Mock private ArchitectureAdvisor architectureAdvisor;

  @Test
  @DisplayName("dispatch fans out code events across code, security, test, and performance analyzers")
  void dispatchFansOutCodeEventsAcrossCodeSecurityTestAndPerformanceAnalyzers() { 
    CodeChangedEvent event =
        new CodeChangedEvent("tenant-a", "project-a", "src/App.ts", "+const answer = 42;"); 
    when(codeQualityAnalyzer.analyze(event)).thenReturn(Promise.of(List.of(insight("code"))));
    when(securityPatternDetector.analyze(event)).thenReturn(Promise.of(List.of(insight("security"))));
    when(testGapAnalyzer.analyze(event)).thenReturn(Promise.of(List.of(insight("tests"))));
    when(performanceAdvisor.analyze(event)).thenReturn(Promise.of(List.of(insight("performance"))));

    AnalysisDispatcher dispatcher = buildDispatcher(); 
    List<AIInsight> insights = runPromise(() -> dispatcher.dispatch(event)); 

    assertThat(insights) 
      .extracting(AIInsight::title) 
      .containsExactlyInAnyOrder("code", "security", "tests", "performance"); 
    verify(codeQualityAnalyzer).analyze(event); 
    verify(securityPatternDetector).analyze(event); 
    verify(testGapAnalyzer).analyze(event); 
    verify(performanceAdvisor).analyze(event); 
    verifyNoInteractions(requirementsConsistencyChecker, architectureAdvisor); 
    assertThat(event.correlationKey()).isEqualTo("tenant-a:project-a:src/App.ts");
    assertThat(event.sourceRef()).isEqualTo("src/App.ts");
  }

  @Test
  @DisplayName("dispatch routes requirement events to consistency checker")
  void dispatchRoutesRequirementEventsToConsistencyChecker() { 
    RequirementChangedEvent event =
        new RequirementChangedEvent( 
            "tenant-b",
            "project-b",
            "REQ-42",
            "Offline mode",
            "The platform must support offline editing with sync recovery and acceptance criteria.",
            List.of("Legacy requirement"));
    when(requirementsConsistencyChecker.analyze(event)) 
        .thenReturn(Promise.of(List.of(insight("requirement"))));

    AnalysisDispatcher dispatcher = buildDispatcher(); 
    List<AIInsight> insights = runPromise(() -> dispatcher.dispatch(event)); 

    assertThat(insights).singleElement().satisfies(insight -> assertThat(insight.title()).isEqualTo("requirement"));
    verify(requirementsConsistencyChecker).analyze(event); 
    verifyNoInteractions( 
      codeQualityAnalyzer, securityPatternDetector, testGapAnalyzer, performanceAdvisor, architectureAdvisor);
    assertThat(event.correlationKey()).isEqualTo("tenant-b:project-b:requirement:REQ-42");
    assertThat(event.sourceRef()).isEqualTo("REQ-42");
  }

  @Test
  @DisplayName("dispatch routes architecture events to architecture advisor and normalizes null values")
  void dispatchRoutesArchitectureEventsToArchitectureAdvisorAndNormalizesNullValues() { 
    ArchitectureChangedEvent event =
        new ArchitectureChangedEvent(null, null, null, null, null, true); 
    when(architectureAdvisor.analyze(event)).thenReturn(Promise.of(List.of(insight("architecture"))));

    AnalysisDispatcher dispatcher = buildDispatcher(); 
    List<AIInsight> insights = runPromise(() -> dispatcher.dispatch(event)); 

    assertThat(insights).singleElement().satisfies(insight -> assertThat(insight.title()).isEqualTo("architecture"));
    verify(architectureAdvisor).analyze(event); 
    verifyNoInteractions( 
      codeQualityAnalyzer,
      securityPatternDetector,
      testGapAnalyzer,
      performanceAdvisor,
      requirementsConsistencyChecker);
    assertThat(event.tenantId()).isEqualTo("unknown-tenant");
    assertThat(event.projectId()).isEqualTo("unknown-project");
    assertThat(event.componentName()).isEqualTo("unknown-component");
    assertThat(event.changeSummary()).isEmpty(); 
    assertThat(event.affectedModules()).isEmpty(); 
    assertThat(event.correlationKey()).isEqualTo("unknown-tenant:unknown-project:architecture:unknown-component");
    assertThat(event.sourceRef()).isEqualTo("unknown-component");
  }

  private AnalysisDispatcher buildDispatcher() { 
    return new AnalysisDispatcher( 
        codeQualityAnalyzer,
        securityPatternDetector,
        testGapAnalyzer,
      performanceAdvisor,
        requirementsConsistencyChecker,
        architectureAdvisor);
  }

  private AIInsight insight(String title) { 
    return new AIInsight( 
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
        List.of("test"),
        Instant.EPOCH,
        false);
  }
}
