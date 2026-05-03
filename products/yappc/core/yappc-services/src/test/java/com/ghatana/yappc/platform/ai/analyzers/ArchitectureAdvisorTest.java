package com.ghatana.yappc.platform.ai.analyzers;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIInterface;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.ArchitectureChangedEvent;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ArchitectureAdvisor Tests")
class ArchitectureAdvisorTest extends EventloopTestBase {

  @Test
  @DisplayName("analyze returns deterministic findings for boundary crossings and broad module impact")
  void analyzeReturnsDeterministicFindingsForBoundaryCrossingsAndBroadModuleImpact() {
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
    ArchitectureAdvisor advisor = new ArchitectureAdvisor(aiService);

    List<AIInsight> insights =
        runPromise(
            () ->
                advisor.analyze(
                    new ArchitectureChangedEvent(
                        "tenant-a",
                        "project-a",
                        "DeploymentRouter",
                        "Introduce helper abstraction spanning platform and product services",
                        List.of("platform/http", "product/api", "product/ui", "product/events"),
                        true)));

    assertThat(insights).hasSize(3);
    assertThat(insights).extracting(AIInsight::title)
        .containsExactlyInAnyOrder(
            "Cross-boundary change detected",
            "High architecture blast radius",
            "Vague architecture abstraction");
    assertThat(aiService.getReasonCallCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("analyze parses structured AI findings when deterministic checks are clean")
  void analyzeParsesStructuredAiFindingsWhenDeterministicChecksAreClean() {
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
    aiService.setReasonResponse(
        "[{\"severity\":\"error\",\"title\":\"Boundary leak\",\"description\":\"Transport type leaked into domain\",\"suggestion\":\"Move mapping to adapter\",\"confidence\":\"0.91\"}]");

    ArchitectureAdvisor advisor = new ArchitectureAdvisor(aiService);
    List<AIInsight> insights =
        runPromise(
            () ->
                advisor.analyze(
                    new ArchitectureChangedEvent(
                        "tenant-a",
                        "project-a",
                        "TenantProjection",
                        "Refine tenant projection contract for deployment observability",
                        List.of("platform/observability", "product/ops"),
                        false)));

    assertThat(insights).singleElement().satisfies(insight -> {
      assertThat(insight.type()).isEqualTo(AIInsight.InsightType.ARCHITECTURE);
      assertThat(insight.title()).isEqualTo("Boundary leak");
      assertThat(insight.confidence()).isEqualTo(0.91);
    });
  }

  @Test
  @DisplayName("analyze falls back to manual review insight for malformed AI response")
  void analyzeFallsBackToManualReviewInsightForMalformedAiResponse() {
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
    aiService.setReasonResponse("not-json");

    ArchitectureAdvisor advisor = new ArchitectureAdvisor(aiService);
    List<AIInsight> insights =
        runPromise(
            () ->
                advisor.analyze(
                    new ArchitectureChangedEvent(
                        "tenant-a",
                        "project-a",
                        "ProjectBoundary",
                        "Tighten explicit contracts between runtime and ops modules",
                        List.of("product/runtime", "product/ops"),
                        false)));

    assertThat(insights).singleElement().satisfies(insight -> {
      assertThat(insight.title()).isEqualTo("Manual architecture review recommended");
      assertThat(insight.description()).isEqualTo("not-json");
    });
  }

  private static final class InMemoryYAPPCAIService implements YAPPCAIInterface {
    private String reasonResponse = null;
    private int reasonCallCount = 0;
    private Map<String, Object> lastReasonContext = null;

    void setReasonResponse(String response) {
      this.reasonResponse = response;
    }

    int getReasonCallCount() {
      return reasonCallCount;
    }

    @Override
    public Promise<String> reason(String prompt, Map<String, Object> context) {
      reasonCallCount++;
      this.lastReasonContext = context;
      return Promise.of(reasonResponse);
    }

    @Override
    public Promise<String> reason(String prompt) {
      return Promise.of(reasonResponse);
    }

    @Override
    public Promise<String> generateCode(String description) {
      return Promise.of("generated code");
    }

    @Override
    public Promise<String> generateCode(String description, Map<String, Object> context) {
      return Promise.of("generated code");
    }

    @Override
    public Promise<String> generateTests(String code) {
      return Promise.of("generated tests");
    }

    @Override
    public Promise<String> generateTests(String code, Map<String, Object> context) {
      return Promise.of("generated tests");
    }
  }
}
