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

@DisplayName("PerformanceAdvisor Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class PerformanceAdvisorTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("analyze emits deterministic warning for await inside loop [GH-90000]")
  void analyzeEmitsDeterministicWarningForAwaitInsideLoop() { // GH-90000
    PerformanceAdvisor advisor =
        new PerformanceAdvisor( // GH-90000
            aiService,
            new ObjectMapper(), // GH-90000
            Clock.fixed(Instant.parse("2026-04-06T12:00:00Z [GH-90000]"), ZoneOffset.UTC));

    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> // GH-90000
                advisor.analyze( // GH-90000
                    new CodeChangedEvent( // GH-90000
                        "tenant-a",
                        "project-a",
                        "src/worker.ts",
                        "for (const item of items) { await process(item); }"))); // GH-90000

    assertThat(insights).hasSize(1); // GH-90000
    assertThat(insights.getFirst().type()).isEqualTo(AIInsight.InsightType.PERFORMANCE); // GH-90000
    assertThat(insights.getFirst().title()).contains("Await inside iterative path [GH-90000]");
  }

  @Test
  @DisplayName("analyze parses structured AI response when deterministic checks are clean [GH-90000]")
  void analyzeParsesStructuredAiResponseWhenDeterministicChecksAreClean() { // GH-90000
    when(aiService.reason(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap())) // GH-90000
        .thenReturn( // GH-90000
            Promise.of( // GH-90000
                "{\"title\":\"Expensive serialization\",\"description\":\"JSON serialization occurs in a render path.\",\"suggestion\":\"Move serialization outside rendering.\",\"severity\":\"warning\",\"confidence\":0.77}"));

    PerformanceAdvisor advisor = new PerformanceAdvisor(aiService); // GH-90000

    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> // GH-90000
                advisor.analyze( // GH-90000
                    new CodeChangedEvent( // GH-90000
                        "tenant-a",
                        "project-a",
                        "src/view.tsx",
                        "const content = rows.map(renderRow);"))); // GH-90000

    assertThat(insights).hasSize(1); // GH-90000
    assertThat(insights.getFirst().title()).isEqualTo("Expensive serialization [GH-90000]");
    assertThat(insights.getFirst().severity()).isEqualTo(AIInsight.InsightSeverity.WARNING); // GH-90000
  }

  @Test
  @DisplayName("analyze returns empty list on malformed AI response [GH-90000]")
  void analyzeReturnsEmptyListOnMalformedAiResponse() { // GH-90000
    when(aiService.reason(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap())) // GH-90000
        .thenReturn(Promise.of("not-json [GH-90000]"));

    PerformanceAdvisor advisor = new PerformanceAdvisor(aiService); // GH-90000

    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> // GH-90000
                advisor.analyze( // GH-90000
                    new CodeChangedEvent( // GH-90000
                        "tenant-a",
                        "project-a",
                        "src/view.tsx",
                        "const content = rows.map(renderRow);"))); // GH-90000

    assertThat(insights).isEmpty(); // GH-90000
  }
}
