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
@DisplayName("CodeQualityAnalyzer Tests")
class CodeQualityAnalyzerTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("analyze returns deterministic findings for TODO and complexity")
  void analyzeReturnsDeterministicFindingsForTodoAndComplexity() {
    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService);

    List<AIInsight> insights =
        runPromise(
            () ->
                analyzer.analyze(
                    new CodeChangedEvent(
                        "tenant-a",
                        "project-a",
                        "src/App.ts",
                        "+ // TODO remove\n+ if (a) {}\n+ if (b) {}\n+ if (c) {}\n+ if (d) {}")));

    assertThat(insights).hasSize(2);
    assertThat(insights).extracting(AIInsight::type).containsOnly(AIInsight.InsightType.CODE_QUALITY);
    verifyNoInteractions(aiService);
  }

  @Test
  @DisplayName("analyze covers wildcard imports and mixed conditional styles")
  void analyzeCoversWildcardImportsAndMixedConditionalStyles() {
    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService);

    List<AIInsight> insights =
        runPromise(
            () ->
                analyzer.analyze(
                    new CodeChangedEvent(
                        "tenant-a",
                        "project-a",
                        "src/Mixed.ts",
                    "const branch = value if enabled\nif(flag) {}\nif (spaced) {}\nif (more) {}\njava.util.*;")));

    assertThat(insights).hasSize(2);
    assertThat(insights).extracting(AIInsight::title)
        .containsExactlyInAnyOrder("High conditional complexity", "Wildcard import introduced");
    assertThat(insights)
        .filteredOn(insight -> insight.title().equals("Wildcard import introduced"))
        .singleElement()
        .satisfies(insight -> assertThat(insight.lineNumber()).isZero());
    verifyNoInteractions(aiService);
  }

  @Test
  @DisplayName("analyze covers FIXME markers and import star syntax")
  void analyzeCoversFixmeMarkersAndImportStarSyntax() {
    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService);

    List<AIInsight> insights =
        runPromise(
            () ->
                analyzer.analyze(
                    new CodeChangedEvent(
                        "tenant-a",
                        "project-a",
                        "src/Fixme.ts",
                        "// FIXME replace placeholder\nimport * as utils from './utils';")));

    assertThat(insights).hasSize(2);
    assertThat(insights).extracting(AIInsight::title)
        .containsExactlyInAnyOrder("Outstanding implementation marker", "Wildcard import introduced");
    verifyNoInteractions(aiService);
  }

  @Test
  @DisplayName("analyze parses AI JSON response when deterministic checks are clean")
  void analyzeParsesAiJsonResponseWhenDeterministicChecksAreClean() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(
            Promise.of(
                "[{\"severity\":\"warning\",\"title\":\"Long method\",\"description\":\"Too long\",\"suggestion\":\"Extract helper\",\"lineNumber\":12,\"confidence\":0.91}]"));

    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService);
    List<AIInsight> insights =
        runPromise(
            () -> analyzer.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = 1;")));

    assertThat(insights).singleElement().satisfies(insight -> {
      assertThat(insight.title()).isEqualTo("Long method");
      assertThat(insight.lineNumber()).isEqualTo(12);
      assertThat(insight.confidence()).isEqualTo(0.91);
    });
  }

  @Test
  @DisplayName("analyze coerces string and missing numeric fields from AI payload")
  void analyzeCoercesStringAndMissingNumericFieldsFromAiPayload() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(
            Promise.of(
                "[{\"severity\":\"warning\",\"title\":\"String fields\",\"description\":\"Uses string numeric fields\",\"suggestion\":\"Refine\",\"lineNumber\":\"7\",\"confidence\":\"0.33\"},{\"title\":\"Missing fields\",\"description\":\"Defaults apply\",\"suggestion\":\"Review\"}]"));

    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService);
    List<AIInsight> insights =
        runPromise(
            () -> analyzer.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = 1;")));

    assertThat(insights).hasSize(2);
    assertThat(insights.get(0).lineNumber()).isEqualTo(7);
    assertThat(insights.get(0).confidence()).isEqualTo(0.33);
    assertThat(insights.get(1).lineNumber()).isZero();
    assertThat(insights.get(1).confidence()).isZero();
    assertThat(insights.get(1).severity()).isEqualTo(AIInsight.InsightSeverity.INFO);
  }

  @Test
  @DisplayName("analyze falls back to manual review insight for malformed AI response")
  void analyzeFallsBackToManualReviewInsightForMalformedAiResponse() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("not-json"));

    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService);
    List<AIInsight> insights =
        runPromise(
            () -> analyzer.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = 1;")));

    assertThat(insights).singleElement().satisfies(insight -> {
      assertThat(insight.title()).isEqualTo("Manual review recommended");
      assertThat(insight.description()).isEqualTo("not-json");
    });
  }

  @Test
  @DisplayName("analyze returns empty list for blank AI response")
  void analyzeReturnsEmptyListForBlankAiResponse() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(" "));

    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService);
    List<AIInsight> insights =
        runPromise(
            () -> analyzer.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = 1;")));

    assertThat(insights).isEmpty();
  }

  @Test
  @DisplayName("analyze returns empty list for null AI response")
  void analyzeReturnsEmptyListForNullAiResponse() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(null));

    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService);
    List<AIInsight> insights =
        runPromise(
            () -> analyzer.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = 1;")));

    assertThat(insights).isEmpty();
  }
}
