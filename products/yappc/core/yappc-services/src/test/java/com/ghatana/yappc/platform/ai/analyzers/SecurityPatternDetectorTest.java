package com.ghatana.yappc.platform.ai.analyzers;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIInterface;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import com.ghatana.yappc.platform.ai.model.AnalysisEvent.CodeChangedEvent;
import io.activej.promise.Promise;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;

@DisplayName("SecurityPatternDetector Tests")
class SecurityPatternDetectorTest extends EventloopTestBase {

  @Test
  @DisplayName("analyze returns deterministic hardcoded secret finding")
  void analyzeReturnsDeterministicHardcodedSecretFinding() {
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
    SecurityPatternDetector detector = new SecurityPatternDetector(aiService); 

    List<AIInsight> insights =
        runPromise( 
            () -> 
                detector.analyze( 
                    new CodeChangedEvent( 
                        "tenant-a",
                        "project-a",
                        "src/Auth.ts",
                        "+ const apiKey = \"secret-value\";")));

    assertThat(insights).singleElement().satisfies(insight -> { 
      assertThat(insight.severity()).isEqualTo(AIInsight.InsightSeverity.CRITICAL); 
      assertThat(insight.title()).isEqualTo("Hardcoded secret detected");
    });
    assertThat(aiService.getReasonCallCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("analyze returns SQL injection and deserialization findings deterministically")
  void analyzeReturnsSqlInjectionAndDeserializationFindingsDeterministically() {
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
    SecurityPatternDetector detector = new SecurityPatternDetector(aiService); 

    List<AIInsight> sqlInsights =
        runPromise( 
            () -> 
                detector.analyze( 
                    new CodeChangedEvent( 
                        "tenant-a",
                        "project-a",
                        "src/Repo.ts",
                        "+ SELECT * FROM users WHERE id = ' + request.getParameter('id')"))); 
    List<AIInsight> deserializationInsights =
        runPromise( 
            () -> 
                detector.analyze( 
                    new CodeChangedEvent( 
                        "tenant-a",
                        "project-a",
                        "src/Deserializer.java",
                        "+ new ObjectInputStream(input).readObject();"))); 

    assertThat(sqlInsights).singleElement().extracting(AIInsight::title).isEqualTo("Potential SQL injection pattern");
    assertThat(deserializationInsights).singleElement().extracting(AIInsight::title).isEqualTo("Unsafe deserialization API");
  }

  @Test
  @DisplayName("analyze parses AI response when deterministic checks are clean")
  void analyzeParsesAiResponseWhenDeterministicChecksAreClean() {
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
    aiService.setReasonResponse(
        "[{\"severity\":\"error\",\"title\":\"Missing validation\",\"description\":\"Input is trusted\",\"suggestion\":\"Validate at the boundary\",\"lineNumber\":8,\"confidence\":0.82}]");

    SecurityPatternDetector detector = new SecurityPatternDetector(aiService); 
    List<AIInsight> insights =
        runPromise( 
            () -> detector.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = sanitize(input);"))); 

    assertThat(insights).singleElement().satisfies(insight -> { 
      assertThat(insight.title()).isEqualTo("Missing validation");
      assertThat(insight.lineNumber()).isEqualTo(8); 
    });
  }

  @Test
  @DisplayName("analyze coerces string and missing numeric fields from AI security payload")
  void analyzeCoercesStringAndMissingNumericFieldsFromAiSecurityPayload() {
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
    aiService.setReasonResponse(
        "[{\"severity\":\"error\",\"title\":\"String numbers\",\"description\":\"desc\",\"suggestion\":\"fix\",\"lineNumber\":\"14\",\"confidence\":\"0.61\"},{\"title\":\"Defaults\",\"description\":\"desc\",\"suggestion\":\"fix\"}]");

    SecurityPatternDetector detector = new SecurityPatternDetector(aiService); 
    List<AIInsight> insights =
        runPromise( 
            () -> detector.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const z = sanitize(input);"))); 

    assertThat(insights).hasSize(2); 
    assertThat(insights.get(0).lineNumber()).isEqualTo(14); 
    assertThat(insights.get(0).confidence()).isEqualTo(0.61); 
    assertThat(insights.get(1).lineNumber()).isZero(); 
    assertThat(insights.get(1).confidence()).isZero(); 
    assertThat(insights.get(1).severity()).isEqualTo(AIInsight.InsightSeverity.WARNING); 
  }

  @Test
  @DisplayName("analyze falls back to manual review for malformed AI response and returns empty for blank")
  void analyzeFallsBackToManualReviewForMalformedAiResponseAndReturnsEmptyForBlank() {
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();

    SecurityPatternDetector detector = new SecurityPatternDetector(aiService);

    aiService.setReasonResponse("not-json");
    List<AIInsight> malformed =
        runPromise(
            () -> detector.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = 1;")));

    aiService.setReasonResponse(" ");
    List<AIInsight> blank =
        runPromise(
            () -> detector.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const y = 2;"))); 

    assertThat(malformed).singleElement().extracting(AIInsight::title).isEqualTo("Manual security review recommended");
    assertThat(blank).isEmpty(); 
  }

  @Test
  @DisplayName("analyze returns empty list for null AI response")
  void analyzeReturnsEmptyListForNullAiResponse() {
    InMemoryYAPPCAIService aiService = new InMemoryYAPPCAIService();
    aiService.setReasonResponse(null);

    SecurityPatternDetector detector = new SecurityPatternDetector(aiService); 
    List<AIInsight> insights =
        runPromise( 
            () -> detector.analyze(new CodeChangedEvent("tenant-a", "project-a", "src/Clean.ts", "+const x = 1;"))); 

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
