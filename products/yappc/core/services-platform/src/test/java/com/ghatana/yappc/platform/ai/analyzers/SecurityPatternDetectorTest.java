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
@DisplayName("SecurityPatternDetector Tests [GH-90000]")
class SecurityPatternDetectorTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("analyze returns deterministic hardcoded secret finding [GH-90000]")
  void analyzeReturnsDeterministicHardcodedSecretFinding() { // GH-90000
    SecurityPatternDetector detector = new SecurityPatternDetector(aiService); // GH-90000

    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> // GH-90000
                detector.analyze( // GH-90000
                    new CodeChangedEvent( // GH-90000
                        "tenant-a",
                        "project-a",
                        "src/Auth.ts",
                        "+ const apiKey = \"secret-value\";")));

    assertThat(insights).singleElement().satisfies(insight -> { // GH-90000
      assertThat(insight.severity()).isEqualTo(AIInsight.InsightSeverity.CRITICAL); // GH-90000
      assertThat(insight.title()).isEqualTo("Hardcoded secret detected [GH-90000]");
    });
    verifyNoInteractions(aiService); // GH-90000
  }

  @Test
  @DisplayName("analyze returns SQL injection and deserialization findings deterministically [GH-90000]")
  void analyzeReturnsSqlInjectionAndDeserializationFindingsDeterministically() { // GH-90000
    SecurityPatternDetector detector = new SecurityPatternDetector(aiService); // GH-90000

    List<AIInsight> sqlInsights =
        runPromise( // GH-90000
            () -> // GH-90000
                detector.analyze( // GH-90000
                    new CodeChangedEvent( // GH-90000
                        "tenant-a",
                        "project-a",
                        "src/Repo.ts",
                        "+ SELECT * FROM users WHERE id = ' + request.getParameter('id')"))); // GH-90000
    List<AIInsight> deserializationInsights =
        runPromise( // GH-90000
            () -> // GH-90000
                detector.analyze( // GH-90000
                    new CodeChangedEvent( // GH-90000
                        "tenant-a",
                        "project-a",
                        "src/Deserializer.java",
                        "+ new ObjectInputStream(input).readObject();"))); // GH-90000

    assertThat(sqlInsights).singleElement().extracting(AIInsight::title).isEqualTo("Potential SQL injection pattern [GH-90000]");
    assertThat(deserializationInsights).singleElement().extracting(AIInsight::title).isEqualTo("Unsafe deserialization API [GH-90000]");
  }

  @Test
  @DisplayName("analyze parses AI response when deterministic checks are clean [GH-90000]")
  void analyzeParsesAiResponseWhenDeterministicChecksAreClean() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn( // GH-90000
            Promise.of( // GH-90000
                "[{\"severity\":\"error\",\"title\":\"Missing validation\",\"description\":\"Input is trusted\",\"suggestion\":\"Validate at the boundary\",\"lineNumber\":8,\"confidence\":0.82}]"));

    SecurityPatternDetector detector = new SecurityPatternDetector(aiService); // GH-90000
    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> detector.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = sanitize(input);"))); // GH-90000

    assertThat(insights).singleElement().satisfies(insight -> { // GH-90000
      assertThat(insight.title()).isEqualTo("Missing validation [GH-90000]");
      assertThat(insight.lineNumber()).isEqualTo(8); // GH-90000
    });
  }

  @Test
  @DisplayName("analyze coerces string and missing numeric fields from AI security payload [GH-90000]")
  void analyzeCoercesStringAndMissingNumericFieldsFromAiSecurityPayload() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn( // GH-90000
            Promise.of( // GH-90000
                "[{\"severity\":\"error\",\"title\":\"String numbers\",\"description\":\"desc\",\"suggestion\":\"fix\",\"lineNumber\":\"14\",\"confidence\":\"0.61\"},{\"title\":\"Defaults\",\"description\":\"desc\",\"suggestion\":\"fix\"}]"));

    SecurityPatternDetector detector = new SecurityPatternDetector(aiService); // GH-90000
    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> detector.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const z = sanitize(input);"))); // GH-90000

    assertThat(insights).hasSize(2); // GH-90000
    assertThat(insights.get(0).lineNumber()).isEqualTo(14); // GH-90000
    assertThat(insights.get(0).confidence()).isEqualTo(0.61); // GH-90000
    assertThat(insights.get(1).lineNumber()).isZero(); // GH-90000
    assertThat(insights.get(1).confidence()).isZero(); // GH-90000
    assertThat(insights.get(1).severity()).isEqualTo(AIInsight.InsightSeverity.WARNING); // GH-90000
  }

  @Test
  @DisplayName("analyze falls back to manual review for malformed AI response and returns empty for blank [GH-90000]")
  void analyzeFallsBackToManualReviewForMalformedAiResponseAndReturnsEmptyForBlank() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of("not-json [GH-90000]"))
        .thenReturn(Promise.of("  [GH-90000]"));

    SecurityPatternDetector detector = new SecurityPatternDetector(aiService); // GH-90000
    List<AIInsight> malformed =
        runPromise( // GH-90000
            () -> detector.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = 1;"))); // GH-90000
    List<AIInsight> blank =
        runPromise( // GH-90000
            () -> detector.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const y = 2;"))); // GH-90000

    assertThat(malformed).singleElement().extracting(AIInsight::title).isEqualTo("Manual security review recommended [GH-90000]");
    assertThat(blank).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("analyze returns empty list for null AI response [GH-90000]")
  void analyzeReturnsEmptyListForNullAiResponse() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(null)); // GH-90000

    SecurityPatternDetector detector = new SecurityPatternDetector(aiService); // GH-90000
    List<AIInsight> insights =
        runPromise( // GH-90000
            () -> detector.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = 1;"))); // GH-90000

    assertThat(insights).isEmpty(); // GH-90000
  }
}
