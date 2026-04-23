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

@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("ArchitectureAdvisor Tests")
class ArchitectureAdvisorTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("analyze returns deterministic findings for boundary crossings and broad module impact")
  void analyzeReturnsDeterministicFindingsForBoundaryCrossingsAndBroadModuleImpact() { // GH-90000
    ArchitectureAdvisor advisor = new ArchitectureAdvisor(aiService); // GH-90000

    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> // GH-90000
                advisor.analyze( // GH-90000
                    new ArchitectureChangedEvent( // GH-90000
                        "tenant-a",
                        "project-a",
                        "DeploymentRouter",
                        "Introduce helper abstraction spanning platform and product services",
                        List.of("platform/http", "product/api", "product/ui", "product/events"), // GH-90000
                        true)));

    assertThat(insights).hasSize(3); // GH-90000
    assertThat(insights).extracting(AIInsight::title) // GH-90000
        .containsExactlyInAnyOrder( // GH-90000
            "Cross-boundary change detected",
            "High architecture blast radius",
            "Vague architecture abstraction");
    verifyNoInteractions(aiService); // GH-90000
  }

  @Test
  @DisplayName("analyze parses structured AI findings when deterministic checks are clean")
  void analyzeParsesStructuredAiFindingsWhenDeterministicChecksAreClean() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn( // GH-90000
            Promise.of( // GH-90000
                "[{\"severity\":\"error\",\"title\":\"Boundary leak\",\"description\":\"Transport type leaked into domain\",\"suggestion\":\"Move mapping to adapter\",\"confidence\":\"0.91\"}]"));

    ArchitectureAdvisor advisor = new ArchitectureAdvisor(aiService); // GH-90000
    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> // GH-90000
                advisor.analyze( // GH-90000
                    new ArchitectureChangedEvent( // GH-90000
                        "tenant-a",
                        "project-a",
                        "TenantProjection",
                        "Refine tenant projection contract for deployment observability",
                        List.of("platform/observability", "product/ops"), // GH-90000
                        false)));

    assertThat(insights).singleElement().satisfies(insight -> { // GH-90000
      assertThat(insight.type()).isEqualTo(AIInsight.InsightType.ARCHITECTURE); // GH-90000
      assertThat(insight.title()).isEqualTo("Boundary leak");
      assertThat(insight.confidence()).isEqualTo(0.91); // GH-90000
    });
  }

  @Test
  @DisplayName("analyze falls back to manual review insight for malformed AI response")
  void analyzeFallsBackToManualReviewInsightForMalformedAiResponse() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("not-json"));

    ArchitectureAdvisor advisor = new ArchitectureAdvisor(aiService); // GH-90000
    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> // GH-90000
                advisor.analyze( // GH-90000
                    new ArchitectureChangedEvent( // GH-90000
                        "tenant-a",
                        "project-a",
                        "ProjectBoundary",
                        "Tighten explicit contracts between runtime and ops modules",
                        List.of("product/runtime", "product/ops"), // GH-90000
                        false)));

    assertThat(insights).singleElement().satisfies(insight -> { // GH-90000
      assertThat(insight.title()).isEqualTo("Manual architecture review recommended");
      assertThat(insight.description()).isEqualTo("not-json");
    });
  }
}
