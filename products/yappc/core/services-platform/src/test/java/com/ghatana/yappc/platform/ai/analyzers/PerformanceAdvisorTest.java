package com.ghatana.yappc.platform.ai.analyzers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.CodeChangedEvent;
import io.activej.promise.Promise;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("PerformanceAdvisor Tests")
@ExtendWith(MockitoExtension.class)
class PerformanceAdvisorTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("analyze emits deterministic warning for await inside loop")
  void analyzeEmitsDeterministicWarningForAwaitInsideLoop() {
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
    when(aiService.reason(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap()))
        .thenReturn(
            Promise.of(
                "{\"title\":\"Expensive serialization\",\"description\":\"JSON serialization occurs in a render path.\",\"suggestion\":\"Move serialization outside rendering.\",\"severity\":\"warning\",\"confidence\":0.77}"));

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
    when(aiService.reason(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap()))
        .thenReturn(Promise.of("not-json"));

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
}
