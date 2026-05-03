package com.ghatana.yappc.platform.ai.analyzers;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIInterface;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.CodeChangedEvent;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CodeQualityAnalyzer Tests")
class CodeQualityAnalyzerTest extends EventloopTestBase {

  @Test
  @DisplayName("analyze returns deterministic findings for TODO and complexity")
  void analyzeReturnsDeterministicFindingsForTodoAndComplexity() {
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
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
    assertThat(aiService.getReasonCallCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("analyze covers wildcard imports and mixed conditional styles")
  void analyzeCoversWildcardImportsAndMixedConditionalStyles() {
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
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
    assertThat(aiService.getReasonCallCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("analyze covers FIXME markers and import star syntax")
  void analyzeCoversFixmeMarkersAndImportStarSyntax() {
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
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
    assertThat(aiService.getReasonCallCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("analyze parses AI JSON response when deterministic checks are clean")
  void analyzeParsesAiJsonResponseWhenDeterministicChecksAreClean() {
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
    aiService.setReasonResponse(
        "[{\"severity\":\"warning\",\"title\":\"Long method\",\"description\":\"Too long\",\"suggestion\":\"Extract helper\",\"lineNumber\":12,\"confidence\":0.91}]");

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
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
    aiService.setReasonResponse(
        "[{\"severity\":\"warning\",\"title\":\"String fields\",\"description\":\"Uses string numeric fields\",\"suggestion\":\"Refine\",\"lineNumber\":\"7\",\"confidence\":\"0.33\"},{\"title\":\"Missing fields\",\"description\":\"Defaults apply\",\"suggestion\":\"Review\"}]");

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
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
    aiService.setReasonResponse("not-json");

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
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
    aiService.setReasonResponse(" ");

    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService);
    List<AIInsight> insights =
        runPromise(
            () -> analyzer.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = 1;")));

    assertThat(insights).isEmpty();
  }

  @Test
  @DisplayName("analyze returns empty list for null AI response")
  void analyzeReturnsEmptyListForNullAiResponse() {
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
    aiService.setReasonResponse(null);

    CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer(aiService);
    List<AIInsight> insights =
        runPromise(
            () -> analyzer.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = 1;")));

    assertThat(insights).isEmpty();
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
