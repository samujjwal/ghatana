package com.ghatana.yappc.platform.ai.analyzers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.RequirementChangedEvent;
import io.activej.promise.Promise;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequirementsConsistencyChecker Tests")
class RequirementsConsistencyCheckerTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("analyze returns deterministic findings for placeholders and duplicate requirements")
  void analyzeReturnsDeterministicFindingsForPlaceholdersAndDuplicateRequirements() {
    RequirementsConsistencyChecker checker = new RequirementsConsistencyChecker(aiService);

    List<AIInsight> insights =
        runPromise(
            () ->
                checker.analyze(
                    new RequirementChangedEvent(
                        "tenant-a",
                        "project-a",
                        "REQ-1",
                        "TBD offline edits",
                        "TBD offline edits",
                        List.of("tbd offline edits"))));

    assertThat(insights).hasSize(3);
    assertThat(insights).extracting(AIInsight::title)
        .containsExactlyInAnyOrder(
            "Requirement lacks implementation detail",
            "Requirement contains placeholders",
            "Potential duplicate requirement");
    verifyNoInteractions(aiService);
  }

  @Test
  @DisplayName("analyze parses structured AI findings when deterministic checks are clean")
  void analyzeParsesStructuredAiFindingsWhenDeterministicChecksAreClean() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(
            Promise.of(
                "[{\"severity\":\"warning\",\"title\":\"Missing acceptance criteria\",\"description\":\"No acceptance criteria linked\",\"suggestion\":\"Add measurable acceptance criteria\",\"confidence\":\"0.87\"}]"));

    RequirementsConsistencyChecker checker = new RequirementsConsistencyChecker(aiService);
    List<AIInsight> insights =
        runPromise(
            () ->
                checker.analyze(
                    new RequirementChangedEvent(
                        "tenant-a",
                        "project-a",
                        "REQ-2",
                        "Offline editing support",
                        "The platform must support offline editing with eventual synchronization and measurable recovery acceptance criteria for mobile and web sessions.",
                        List.of("Sync recovery requirement"))));

    assertThat(insights).singleElement().satisfies(insight -> {
      assertThat(insight.type()).isEqualTo(AIInsight.InsightType.REQUIREMENT);
      assertThat(insight.title()).isEqualTo("Missing acceptance criteria");
      assertThat(insight.confidence()).isEqualTo(0.87);
    });
  }

  @Test
  @DisplayName("analyze falls back to manual review insight for malformed AI response")
  void analyzeFallsBackToManualReviewInsightForMalformedAiResponse() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("not-json"));

    RequirementsConsistencyChecker checker = new RequirementsConsistencyChecker(aiService);
    List<AIInsight> insights =
        runPromise(
            () ->
                checker.analyze(
                    new RequirementChangedEvent(
                        "tenant-a",
                        "project-a",
                        "REQ-3",
                        "Clear requirement",
                        "The platform must provide operator-visible audit trails for generated changes, including actor identity, timestamps, policy checks, and rollback steps.",
                        List.of("Security audit requirement"))));

    assertThat(insights).singleElement().satisfies(insight -> {
      assertThat(insight.title()).isEqualTo("Manual requirement review recommended");
      assertThat(insight.description()).isEqualTo("not-json");
    });
  }
}