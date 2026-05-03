package com.ghatana.yappc.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIInterface;
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
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AnalysisDispatcher Tests")
class AnalysisDispatcherTest extends EventloopTestBase {

  private InMemoryCodeQualityAnalyzer codeQualityAnalyzer;
  private InMemorySecurityPatternDetector securityPatternDetector;
  private InMemoryTestGapAnalyzer testGapAnalyzer;
  private InMemoryPerformanceAdvisor performanceAdvisor;
  private InMemoryRequirementsConsistencyChecker requirementsConsistencyChecker;
  private InMemoryArchitectureAdvisor architectureAdvisor;

  @Test
  @DisplayName("dispatch fans out code events across code, security, test, and performance analyzers")
  void dispatchFansOutCodeEventsAcrossCodeSecurityTestAndPerformanceAnalyzers() {
    codeQualityAnalyzer = new InMemoryCodeQualityAnalyzer();
    securityPatternDetector = new InMemorySecurityPatternDetector();
    testGapAnalyzer = new InMemoryTestGapAnalyzer();
    performanceAdvisor = new InMemoryPerformanceAdvisor();
    requirementsConsistencyChecker = new InMemoryRequirementsConsistencyChecker();
    architectureAdvisor = new InMemoryArchitectureAdvisor();

    CodeChangedEvent event =
        new CodeChangedEvent("tenant-a", "project-a", "src/App.ts", "+const answer = 42;");
    codeQualityAnalyzer.setAnalyzeResult(List.of(insight("code")));
    securityPatternDetector.setAnalyzeResult(List.of(insight("security")));
    testGapAnalyzer.setAnalyzeResult(List.of(insight("tests")));
    performanceAdvisor.setAnalyzeResult(List.of(insight("performance")));

    AnalysisDispatcher dispatcher = buildDispatcher();
    List<AIInsight> insights = runPromise(() -> dispatcher.dispatch(event));

    assertThat(insights)
      .extracting(AIInsight::title)
      .containsExactlyInAnyOrder("code", "security", "tests", "performance");
    assertThat(codeQualityAnalyzer.getAnalyzeCallCount()).isEqualTo(1);
    assertThat(securityPatternDetector.getAnalyzeCallCount()).isEqualTo(1);
    assertThat(testGapAnalyzer.getAnalyzeCallCount()).isEqualTo(1);
    assertThat(performanceAdvisor.getAnalyzeCallCount()).isEqualTo(1);
    assertThat(requirementsConsistencyChecker.getAnalyzeCallCount()).isEqualTo(0);
    assertThat(architectureAdvisor.getAnalyzeCallCount()).isEqualTo(0);
    assertThat(event.correlationKey()).isEqualTo("tenant-a:project-a:src/App.ts");
    assertThat(event.sourceRef()).isEqualTo("src/App.ts");
  }

  @Test
  @DisplayName("dispatch routes requirement events to consistency checker")
  void dispatchRoutesRequirementEventsToConsistencyChecker() {
    codeQualityAnalyzer = new InMemoryCodeQualityAnalyzer();
    securityPatternDetector = new InMemorySecurityPatternDetector();
    testGapAnalyzer = new InMemoryTestGapAnalyzer();
    performanceAdvisor = new InMemoryPerformanceAdvisor();
    requirementsConsistencyChecker = new InMemoryRequirementsConsistencyChecker();
    architectureAdvisor = new InMemoryArchitectureAdvisor();

    RequirementChangedEvent event =
        new RequirementChangedEvent(
            "tenant-b",
            "project-b",
            "REQ-42",
            "Offline mode",
            "The platform must support offline editing with sync recovery and acceptance criteria.",
            List.of("Legacy requirement"));
    requirementsConsistencyChecker.setAnalyzeResult(List.of(insight("requirement")));

    AnalysisDispatcher dispatcher = buildDispatcher();
    List<AIInsight> insights = runPromise(() -> dispatcher.dispatch(event));

    assertThat(insights).singleElement().satisfies(insight -> assertThat(insight.title()).isEqualTo("requirement"));
    assertThat(requirementsConsistencyChecker.getAnalyzeCallCount()).isEqualTo(1);
    assertThat(codeQualityAnalyzer.getAnalyzeCallCount()).isEqualTo(0);
    assertThat(securityPatternDetector.getAnalyzeCallCount()).isEqualTo(0);
    assertThat(testGapAnalyzer.getAnalyzeCallCount()).isEqualTo(0);
    assertThat(performanceAdvisor.getAnalyzeCallCount()).isEqualTo(0);
    assertThat(architectureAdvisor.getAnalyzeCallCount()).isEqualTo(0);
    assertThat(event.correlationKey()).isEqualTo("tenant-b:project-b:requirement:REQ-42");
    assertThat(event.sourceRef()).isEqualTo("REQ-42");
  }

  @Test
  @DisplayName("dispatch routes architecture events to architecture advisor and normalizes null values")
  void dispatchRoutesArchitectureEventsToArchitectureAdvisorAndNormalizesNullValues() {
    codeQualityAnalyzer = new InMemoryCodeQualityAnalyzer();
    securityPatternDetector = new InMemorySecurityPatternDetector();
    testGapAnalyzer = new InMemoryTestGapAnalyzer();
    performanceAdvisor = new InMemoryPerformanceAdvisor();
    requirementsConsistencyChecker = new InMemoryRequirementsConsistencyChecker();
    architectureAdvisor = new InMemoryArchitectureAdvisor();

    ArchitectureChangedEvent event =
        new ArchitectureChangedEvent(null, null, null, null, null, true);
    architectureAdvisor.setAnalyzeResult(List.of(insight("architecture")));

    AnalysisDispatcher dispatcher = buildDispatcher();
    List<AIInsight> insights = runPromise(() -> dispatcher.dispatch(event));

    assertThat(insights).singleElement().satisfies(insight -> assertThat(insight.title()).isEqualTo("architecture"));
    assertThat(architectureAdvisor.getAnalyzeCallCount()).isEqualTo(1);
    assertThat(codeQualityAnalyzer.getAnalyzeCallCount()).isEqualTo(0);
    assertThat(securityPatternDetector.getAnalyzeCallCount()).isEqualTo(0);
    assertThat(testGapAnalyzer.getAnalyzeCallCount()).isEqualTo(0);
    assertThat(performanceAdvisor.getAnalyzeCallCount()).isEqualTo(0);
    assertThat(requirementsConsistencyChecker.getAnalyzeCallCount()).isEqualTo(0);
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
        new CodeQualityAnalyzer(new InMemoryYAPPCAIService()),
        new SecurityPatternDetector(new InMemoryYAPPCAIService()),
        new TestGapAnalyzer(new InMemoryYAPPCAIService(), (projectId, filePath) -> Promise.of(new TestGapAnalyzer.CoverageSnapshot(0.0, 0.0, 0, 0, List.of())), (projectId, filePath) -> Promise.of(new TestGapAnalyzer.RecentTestHistory(List.of(), 0, 0, 0))),
        new PerformanceAdvisor(new InMemoryYAPPCAIService()),
        new RequirementsConsistencyChecker(new InMemoryYAPPCAIService()),
        new ArchitectureAdvisor(new InMemoryYAPPCAIService()));
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

  private static final class InMemoryCodeQualityAnalyzer {
    private List<AIInsight> analyzeResult = null;
    private int analyzeCallCount = 0;

    void setAnalyzeResult(List<AIInsight> result) {
      this.analyzeResult = result;
    }

    int getAnalyzeCallCount() {
      return analyzeCallCount;
    }

    Promise<List<AIInsight>> analyze(CodeChangedEvent event) {
      analyzeCallCount++;
      return Promise.of(analyzeResult);
    }
  }

  private static final class InMemorySecurityPatternDetector {
    private List<AIInsight> analyzeResult = null;
    private int analyzeCallCount = 0;

    void setAnalyzeResult(List<AIInsight> result) {
      this.analyzeResult = result;
    }

    int getAnalyzeCallCount() {
      return analyzeCallCount;
    }

    Promise<List<AIInsight>> analyze(CodeChangedEvent event) {
      analyzeCallCount++;
      return Promise.of(analyzeResult);
    }
  }

  private static final class InMemoryTestGapAnalyzer {
    private List<AIInsight> analyzeResult = null;
    private int analyzeCallCount = 0;

    void setAnalyzeResult(List<AIInsight> result) {
      this.analyzeResult = result;
    }

    int getAnalyzeCallCount() {
      return analyzeCallCount;
    }

    Promise<List<AIInsight>> analyze(CodeChangedEvent event) {
      analyzeCallCount++;
      return Promise.of(analyzeResult);
    }
  }

  private static final class InMemoryPerformanceAdvisor {
    private List<AIInsight> analyzeResult = null;
    private int analyzeCallCount = 0;

    void setAnalyzeResult(List<AIInsight> result) {
      this.analyzeResult = result;
    }

    int getAnalyzeCallCount() {
      return analyzeCallCount;
    }

    Promise<List<AIInsight>> analyze(CodeChangedEvent event) {
      analyzeCallCount++;
      return Promise.of(analyzeResult);
    }
  }

  private static final class InMemoryRequirementsConsistencyChecker {
    private List<AIInsight> analyzeResult = null;
    private int analyzeCallCount = 0;

    void setAnalyzeResult(List<AIInsight> result) {
      this.analyzeResult = result;
    }

    int getAnalyzeCallCount() {
      return analyzeCallCount;
    }

    Promise<List<AIInsight>> analyze(RequirementChangedEvent event) {
      analyzeCallCount++;
      return Promise.of(analyzeResult);
    }
  }

  private static final class InMemoryArchitectureAdvisor {
    private List<AIInsight> analyzeResult = null;
    private int analyzeCallCount = 0;

    void setAnalyzeResult(List<AIInsight> result) {
      this.analyzeResult = result;
    }

    int getAnalyzeCallCount() {
      return analyzeCallCount;
    }

    Promise<List<AIInsight>> analyze(ArchitectureChangedEvent event) {
      analyzeCallCount++;
      return Promise.of(analyzeResult);
    }
  }

  private static final class InMemoryYAPPCAIService implements YAPPCAIInterface {
    private String reasonResponse = null;
    private int reasonCallCount = 0;

    void setReasonResponse(String response) {
      this.reasonResponse = response;
    }

    @Override
    public Promise<String> reason(String question) {
      reasonCallCount++;
      return Promise.of(reasonResponse != null ? reasonResponse : "AI response");
    }

    @Override
    public Promise<String> reason(String question, Map<String, Object> context) {
      reasonCallCount++;
      return Promise.of(reasonResponse != null ? reasonResponse : "AI response");
    }

    @Override
    public Promise<String> generateCode(String description) {
      reasonCallCount++;
      return Promise.of("generated code");
    }

    @Override
    public Promise<String> generateCode(String description, Map<String, Object> context) {
      reasonCallCount++;
      return Promise.of("generated code");
    }

    @Override
    public Promise<String> generateTests(String code) {
      reasonCallCount++;
      return Promise.of("generated tests");
    }

    @Override
    public Promise<String> generateTests(String code, Map<String, Object> context) {
      reasonCallCount++;
      return Promise.of("generated tests");
    }
  }
}
