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
@DisplayName("CodeQualityAnalyzer Tests")
class CodeQualityAnalyzerTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("analyze returns deterministic findings for TODO and complexity")
  void analyzeReturnsDeterministicFindingsForTodoAndComplexity() { // GH-90000
    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService); // GH-90000

    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> // GH-90000
                analyzer.analyze( // GH-90000
                    new CodeChangedEvent( // GH-90000
                        "tenant-a",
                        "project-a",
                        "src/App.ts",
                        "+ // TODO remove\n+ if (a) {}\n+ if (b) {}\n+ if (c) {}\n+ if (d) {}"))); // GH-90000

    assertThat(insights).hasSize(2); // GH-90000
    assertThat(insights).extracting(AIInsight::type).containsOnly(AIInsight.InsightType.CODE_QUALITY); // GH-90000
    verifyNoInteractions(aiService); // GH-90000
  }

  @Test
  @DisplayName("analyze covers wildcard imports and mixed conditional styles")
  void analyzeCoversWildcardImportsAndMixedConditionalStyles() { // GH-90000
    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService); // GH-90000

    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> // GH-90000
                analyzer.analyze( // GH-90000
                    new CodeChangedEvent( // GH-90000
                        "tenant-a",
                        "project-a",
                        "src/Mixed.ts",
                    "const branch = value if enabled\nif(flag) {}\nif (spaced) {}\nif (more) {}\njava.util.*;"))); // GH-90000

    assertThat(insights).hasSize(2); // GH-90000
    assertThat(insights).extracting(AIInsight::title) // GH-90000
        .containsExactlyInAnyOrder("High conditional complexity", "Wildcard import introduced"); // GH-90000
    assertThat(insights) // GH-90000
        .filteredOn(insight -> insight.title().equals("Wildcard import introduced"))
        .singleElement() // GH-90000
        .satisfies(insight -> assertThat(insight.lineNumber()).isZero()); // GH-90000
    verifyNoInteractions(aiService); // GH-90000
  }

  @Test
  @DisplayName("analyze covers FIXME markers and import star syntax")
  void analyzeCoversFixmeMarkersAndImportStarSyntax() { // GH-90000
    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService); // GH-90000

    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> // GH-90000
                analyzer.analyze( // GH-90000
                    new CodeChangedEvent( // GH-90000
                        "tenant-a",
                        "project-a",
                        "src/Fixme.ts",
                        "// FIXME replace placeholder\nimport * as utils from './utils';")));

    assertThat(insights).hasSize(2); // GH-90000
    assertThat(insights).extracting(AIInsight::title) // GH-90000
        .containsExactlyInAnyOrder("Outstanding implementation marker", "Wildcard import introduced"); // GH-90000
    verifyNoInteractions(aiService); // GH-90000
  }

  @Test
  @DisplayName("analyze parses AI JSON response when deterministic checks are clean")
  void analyzeParsesAiJsonResponseWhenDeterministicChecksAreClean() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn( // GH-90000
            Promise.of( // GH-90000
                "[{\"severity\":\"warning\",\"title\":\"Long method\",\"description\":\"Too long\",\"suggestion\":\"Extract helper\",\"lineNumber\":12,\"confidence\":0.91}]"));

    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService); // GH-90000
    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> analyzer.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = 1;"))); // GH-90000

    assertThat(insights).singleElement().satisfies(insight -> { // GH-90000
      assertThat(insight.title()).isEqualTo("Long method");
      assertThat(insight.lineNumber()).isEqualTo(12); // GH-90000
      assertThat(insight.confidence()).isEqualTo(0.91); // GH-90000
    });
  }

  @Test
  @DisplayName("analyze coerces string and missing numeric fields from AI payload")
  void analyzeCoercesStringAndMissingNumericFieldsFromAiPayload() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn( // GH-90000
            Promise.of( // GH-90000
                "[{\"severity\":\"warning\",\"title\":\"String fields\",\"description\":\"Uses string numeric fields\",\"suggestion\":\"Refine\",\"lineNumber\":\"7\",\"confidence\":\"0.33\"},{\"title\":\"Missing fields\",\"description\":\"Defaults apply\",\"suggestion\":\"Review\"}]"));

    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService); // GH-90000
    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> analyzer.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = 1;"))); // GH-90000

    assertThat(insights).hasSize(2); // GH-90000
    assertThat(insights.get(0).lineNumber()).isEqualTo(7); // GH-90000
    assertThat(insights.get(0).confidence()).isEqualTo(0.33); // GH-90000
    assertThat(insights.get(1).lineNumber()).isZero(); // GH-90000
    assertThat(insights.get(1).confidence()).isZero(); // GH-90000
    assertThat(insights.get(1).severity()).isEqualTo(AIInsight.InsightSeverity.INFO); // GH-90000
  }

  @Test
  @DisplayName("analyze falls back to manual review insight for malformed AI response")
  void analyzeFallsBackToManualReviewInsightForMalformedAiResponse() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("not-json"));

    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService); // GH-90000
    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> analyzer.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = 1;"))); // GH-90000

    assertThat(insights).singleElement().satisfies(insight -> { // GH-90000
      assertThat(insight.title()).isEqualTo("Manual review recommended");
      assertThat(insight.description()).isEqualTo("not-json");
    });
  }

  @Test
  @DisplayName("analyze returns empty list for blank AI response")
  void analyzeReturnsEmptyListForBlankAiResponse() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(" "));

    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService); // GH-90000
    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> analyzer.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = 1;"))); // GH-90000

    assertThat(insights).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("analyze returns empty list for null AI response")
  void analyzeReturnsEmptyListForNullAiResponse() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(null)); // GH-90000

    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService); // GH-90000
    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> analyzer.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = 1;"))); // GH-90000

    assertThat(insights).isEmpty(); // GH-90000
  }
}
