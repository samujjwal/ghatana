package com.ghatana.yappc.platform.ai.analyzers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.ArchitectureChangedEvent;
import io.activej.promise.Promise;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArchitectureAdvisor Tests")
class ArchitectureAdvisorTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("analyze returns deterministic findings for boundary crossings and broad module impact")
  void analyzeReturnsDeterministicFindingsForBoundaryCrossingsAndBroadModuleImpact() {
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
    verifyNoInteractions(aiService);
  }

  @Test
  @DisplayName("analyze parses structured AI findings when deterministic checks are clean")
  void analyzeParsesStructuredAiFindingsWhenDeterministicChecksAreClean() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(
            Promise.of(
                "[{\"severity\":\"error\",\"title\":\"Boundary leak\",\"description\":\"Transport type leaked into domain\",\"suggestion\":\"Move mapping to adapter\",\"confidence\":\"0.91\"}]"));

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
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("not-json"));

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
}
