package com.ghatana.yappc.platform.ai.analyzers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIInterface;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.CodeChangedEvent;
import io.activej.promise.Promise;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PerformanceAdvisor Tests")
class PerformanceAdvisorTest extends EventloopTestBase {

  @Test
  @DisplayName("analyze emits deterministic warning for await inside loop")
  void analyzeEmitsDeterministicWarningForAwaitInsideLoop() {
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
    PerformanceAdvisor advisor =
        new PerformanceAdvisor(
            aiService,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-04-06T12:00:00Z"), ZoneOffset.UTC));

    List<AIInsight> insights =
        runPromise(
            () ->
                advisor.analyze(
                    new CodeChangedEvent(
                        "tenant-a",
                        "project-a",
                        "src/worker.ts",
                        "for (const item of items) { await process(item); }")));

    assertThat(insights).hasSize(1);
    assertThat(insights.getFirst().type()).isEqualTo(AIInsight.InsightType.PERFORMANCE);
    assertThat(insights.getFirst().title()).contains("Await inside iterative path");
  }

  @Test
  @DisplayName("analyze parses structured AI response when deterministic checks are clean")
  void analyzeParsesStructuredAiResponseWhenDeterministicChecksAreClean() {
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
    aiService.setReasonResponse(
        "{\"title\":\"Expensive serialization\",\"description\":\"JSON serialization occurs in a render path.\",\"suggestion\":\"Move serialization outside rendering.\",\"severity\":\"warning\",\"confidence\":0.77}");

    PerformanceAdvisor advisor = new PerformanceAdvisor(aiService);

    List<AIInsight> insights =
        runPromise(
            () ->
                advisor.analyze(
                    new CodeChangedEvent(
                        "tenant-a",
                        "project-a",
                        "src/view.tsx",
                        "const content = rows.map(renderRow);")));

    assertThat(insights).hasSize(1);
    assertThat(insights.getFirst().title()).isEqualTo("Expensive serialization");
    assertThat(insights.getFirst().severity()).isEqualTo(AIInsight.InsightSeverity.WARNING);
  }

  @Test
  @DisplayName("analyze returns empty list on malformed AI response")
  void analyzeReturnsEmptyListOnMalformedAiResponse() {
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
    aiService.setReasonResponse("not-json");

    PerformanceAdvisor advisor = new PerformanceAdvisor(aiService);

    List<AIInsight> insights =
        runPromise(
            () ->
                advisor.analyze(
                    new CodeChangedEvent(
                        "tenant-a",
                        "project-a",
                        "src/view.tsx",
                        "const content = rows.map(renderRow);")));

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
