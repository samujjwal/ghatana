package com.ghatana.yappc.infrastructure.ai.scoring;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.ai.ConfidenceScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Deterministic fallback and output-validation tests for ConfidenceScoringService.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Scores are always within [0.0, 1.0] regardless of input shape</li>
 *   <li>Placeholder/unsafe outputs are penalised (lower correctness score)</li>
 *   <li>Metrics are always emitted — no silent failures in the scoring path</li>
 *   <li>Deterministic: identical inputs produce identical scores</li>
 *   <li>Edge inputs (empty, null-text, very long) do not throw</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Deterministic output-validation tests for AI confidence scoring
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConfidenceScoringService — deterministic fallback and output validation")
class ConfidenceScoringServiceTest extends EventloopTestBase {

    @Mock
    private MetricsCollector metrics;

    private ConfidenceScoringService service;

    @BeforeEach
    void setUp() {
        service = new ConfidenceScoringService(metrics);
    }

    // =========================================================================
    // SCORE BOUNDS — all dimensions must stay in [0.0, 1.0]
    // =========================================================================

    @Nested
    @DisplayName("Score bounds — always within [0.0, 1.0]")
    class ScoreBoundsTests {

        @Test
        @DisplayName("empty output text produces bounded scores")
        void emptyOutputProducesBoundedScores() {
            CompletionResult result = CompletionResult.builder().text("").modelUsed("gpt-4").build();
            CompletionRequest request = CompletionRequest.builder().prompt("generate code").build();

            ConfidenceScore score = runPromise(() -> service.scoreCompletion(result, request));

            assertBounded(score);
        }

        @Test
        @DisplayName("very short output produces bounded scores")
        void veryShortOutputProducesBoundedScores() {
            CompletionResult result = CompletionResult.builder().text("ok").modelUsed("gpt-4").build();
            CompletionRequest request = CompletionRequest.builder().prompt("test").build();

            ConfidenceScore score = runPromise(() -> service.scoreCompletion(result, request));

            assertBounded(score);
        }

        @Test
        @DisplayName("very long output produces bounded scores")
        void veryLongOutputProducesBoundedScores() {
            String longText = "public class Main {\n".repeat(200) + "}";
            CompletionResult result = CompletionResult.builder().text(longText).modelUsed("gpt-4").build();
            CompletionRequest request = CompletionRequest.builder().prompt("generate a Java class").build();

            ConfidenceScore score = runPromise(() -> service.scoreCompletion(result, request));

            assertBounded(score);
        }

        @Test
        @DisplayName("output with only whitespace produces bounded scores")
        void whitespaceOnlyOutputProducesBoundedScores() {
            CompletionResult result = CompletionResult.builder().text("   \n\t  ").modelUsed("gpt-4").build();
            CompletionRequest request = CompletionRequest.builder().prompt("generate").build();

            ConfidenceScore score = runPromise(() -> service.scoreCompletion(result, request));

            assertBounded(score);
        }

        private void assertBounded(ConfidenceScore score) {
            assertThat(score.overall()).isBetween(0.0, 1.0);
            assertThat(score.completeness()).isBetween(0.0, 1.0);
            assertThat(score.correctness()).isBetween(0.0, 1.0);
            assertThat(score.consistency()).isBetween(0.0, 1.0);
            assertThat(score.complexity()).isBetween(0.0, 1.0);
        }
    }

    // =========================================================================
    // PLACEHOLDER / UNSAFE OUTPUT PENALISATION
    // =========================================================================

    @Nested
    @DisplayName("Unsafe output penalisation — placeholder markers lower correctness")
    class UnsafeOutputTests {

        @Test
        @DisplayName("output containing TODO marker has lower correctness than clean output")
        void todoMarkerLowersCorrectnessScore() {
            CompletionRequest request = CompletionRequest.builder().prompt("generate code").build();

            CompletionResult cleanResult = CompletionResult.builder()
                .text("public class Service { public void execute() { doWork(); } }")
                .modelUsed("gpt-4").build();
            CompletionResult placeholderResult = CompletionResult.builder()
                .text("public class Service { public void execute() { TODO: implement } }")
                .modelUsed("gpt-4").build();

            ConfidenceScore cleanScore = runPromise(() -> service.scoreCompletion(cleanResult, request));
            ConfidenceScore placeholderScore = runPromise(() -> service.scoreCompletion(placeholderResult, request));

            assertThat(placeholderScore.correctness())
                .as("placeholder output correctness should be lower than clean output")
                .isLessThan(cleanScore.correctness());
        }

        @Test
        @DisplayName("output containing FIXME marker has lower correctness than clean output")
        void fixmeMarkerLowersCorrectnessScore() {
            CompletionRequest request = CompletionRequest.builder().prompt("generate code").build();

            CompletionResult cleanResult = CompletionResult.builder()
                .text("public class Validator { public boolean validate(String s) { return s != null; } }")
                .modelUsed("gpt-4").build();
            CompletionResult fixmeResult = CompletionResult.builder()
                .text("public class Validator { public boolean validate(String s) { FIXME: broken logic return true; } }")
                .modelUsed("gpt-4").build();

            ConfidenceScore cleanScore = runPromise(() -> service.scoreCompletion(cleanResult, request));
            ConfidenceScore fixmeScore = runPromise(() -> service.scoreCompletion(fixmeResult, request));

            assertThat(fixmeScore.correctness())
                .as("FIXME marker should lower correctness vs clean output")
                .isLessThan(cleanScore.correctness());
        }

        @Test
        @DisplayName("output containing error keywords has lower correctness")
        void errorKeywordsLowerCorrectnessScore() {
            CompletionRequest request = CompletionRequest.builder().prompt("generate handler").build();

            CompletionResult errorResult = CompletionResult.builder()
                .text("Error: unable to generate the requested code. Exception occurred.")
                .modelUsed("gpt-4").build();
            CompletionResult normalResult = CompletionResult.builder()
                .text("public class Handler { public void handle() { process(); } }")
                .modelUsed("gpt-4").build();

            ConfidenceScore errorScore = runPromise(() -> service.scoreCompletion(errorResult, request));
            ConfidenceScore normalScore = runPromise(() -> service.scoreCompletion(normalResult, request));

            assertThat(errorScore.correctness())
                .as("error keywords should lower correctness vs normal output")
                .isLessThan(normalScore.correctness());
        }
    }

    // =========================================================================
    // DETERMINISM — same input → same score
    // =========================================================================

    @Nested
    @DisplayName("Determinism — identical inputs produce identical scores")
    class DeterminismTests {

        @Test
        @DisplayName("scoring same result twice returns equal scores")
        void samInputReturnsSameScore() {
            CompletionResult result = CompletionResult.builder()
                .text("public class Foo { public void bar() {} }")
                .modelUsed("gpt-4").build();
            CompletionRequest request = CompletionRequest.builder()
                .prompt("generate a Java class").build();

            ConfidenceScore score1 = runPromise(() -> service.scoreCompletion(result, request));
            ConfidenceScore score2 = runPromise(() -> service.scoreCompletion(result, request));

            assertThat(score1.overall()).isEqualTo(score2.overall());
            assertThat(score1.completeness()).isEqualTo(score2.completeness());
            assertThat(score1.correctness()).isEqualTo(score2.correctness());
            assertThat(score1.consistency()).isEqualTo(score2.consistency());
            assertThat(score1.complexity()).isEqualTo(score2.complexity());
        }
    }

    // =========================================================================
    // LANGUAGE CONSISTENCY — prompt/output language match boosts score
    // =========================================================================

    @Nested
    @DisplayName("Language consistency — prompt/output language alignment affects consistency")
    class LanguageConsistencyTests {

        @Test
        @DisplayName("Java output for Java prompt has higher consistency than mismatched output")
        void javaOutputMatchesJavaPrompt() {
            CompletionRequest javaPrompt = CompletionRequest.builder()
                .prompt("Write a Java service class").build();

            CompletionResult javaOutput = CompletionResult.builder()
                .text("public class MyService { private final Repository repo; }")
                .modelUsed("gpt-4").build();
            CompletionResult pythonOutput = CompletionResult.builder()
                .text("def my_service(): pass")
                .modelUsed("gpt-4").build();

            ConfidenceScore javaScore = runPromise(() -> service.scoreCompletion(javaOutput, javaPrompt));
            ConfidenceScore pythonScore = runPromise(() -> service.scoreCompletion(pythonOutput, javaPrompt));

            assertThat(javaScore.consistency())
                .as("Java output should have higher consistency for a Java prompt")
                .isGreaterThan(pythonScore.consistency());
        }

        @Test
        @DisplayName("TypeScript output for TypeScript prompt has higher consistency")
        void typeScriptOutputMatchesTypeScriptPrompt() {
            CompletionRequest tsPrompt = CompletionRequest.builder()
                .prompt("Write a TypeScript interface").build();

            CompletionResult tsOutput = CompletionResult.builder()
                .text("export interface UserService { getUser(id: string): Promise<User>; }")
                .modelUsed("gpt-4").build();
            CompletionResult javaOutput = CompletionResult.builder()
                .text("public class UserService { public User getUser(String id) {} }")
                .modelUsed("gpt-4").build();

            ConfidenceScore tsScore = runPromise(() -> service.scoreCompletion(tsOutput, tsPrompt));
            ConfidenceScore javaScore = runPromise(() -> service.scoreCompletion(javaOutput, tsPrompt));

            assertThat(tsScore.consistency())
                .as("TypeScript output should have higher consistency for a TypeScript prompt")
                .isGreaterThan(javaScore.consistency());
        }
    }

    // =========================================================================
    // OBSERVABILITY — metrics always emitted
    // =========================================================================

    @Nested
    @DisplayName("Observability — metrics always emitted on scoring")
    class ObservabilityTests {

        @Test
        @DisplayName("scoreCompletion always emits overall confidence metric")
        void alwaysEmitsOverallMetric() {
            CompletionResult result = CompletionResult.builder().text("some output").modelUsed("gpt-4").build();
            CompletionRequest request = CompletionRequest.builder().prompt("prompt").build();

            runPromise(() -> service.scoreCompletion(result, request));

            verify(metrics).recordConfidenceScore(eq("ai.confidence.overall"), anyDouble());
        }

        @Test
        @DisplayName("scoreCompletion always emits completeness metric")
        void alwaysEmitsCompletenessMetric() {
            CompletionResult result = CompletionResult.builder().text("output").modelUsed("gpt-4").build();
            CompletionRequest request = CompletionRequest.builder().prompt("prompt").build();

            runPromise(() -> service.scoreCompletion(result, request));

            verify(metrics).recordConfidenceScore(eq("ai.confidence.completeness"), anyDouble());
        }

        @Test
        @DisplayName("scoreCompletion always emits correctness metric")
        void alwaysEmitsCorrectnessMetric() {
            CompletionResult result = CompletionResult.builder().text("output").modelUsed("gpt-4").build();
            CompletionRequest request = CompletionRequest.builder().prompt("prompt").build();

            runPromise(() -> service.scoreCompletion(result, request));

            verify(metrics).recordConfidenceScore(eq("ai.confidence.correctness"), anyDouble());
        }

        @Test
        @DisplayName("scoreCompletion always increments review-priority counter")
        void alwaysEmitsReviewPriorityCounter() {
            CompletionResult result = CompletionResult.builder().text("output").modelUsed("gpt-4").build();
            CompletionRequest request = CompletionRequest.builder().prompt("prompt").build();

            runPromise(() -> service.scoreCompletion(result, request));

            verify(metrics).incrementCounter(eq("ai.scoring.completed"), eq("priority"), anyString());
        }

        @Test
        @DisplayName("high-placeholder output still emits all metrics")
        void highPlaceholderOutputStillEmitsMetrics() {
            CompletionResult result = CompletionResult.builder()
                .text("TODO FIXME XXX placeholder example")
                .modelUsed("gpt-4").build();
            CompletionRequest request = CompletionRequest.builder().prompt("generate").build();

            runPromise(() -> service.scoreCompletion(result, request));

            verify(metrics).recordConfidenceScore(eq("ai.confidence.overall"), anyDouble());
            verify(metrics).recordConfidenceScore(eq("ai.confidence.completeness"), anyDouble());
            verify(metrics).recordConfidenceScore(eq("ai.confidence.correctness"), anyDouble());
            verify(metrics).incrementCounter(eq("ai.scoring.completed"), eq("priority"), anyString());
        }
    }

    // =========================================================================
    // REVIEW PRIORITY — low-confidence outputs get elevated review priority
    // =========================================================================

    @Nested
    @DisplayName("Review priority — low-score outputs get appropriate priority")
    class ReviewPriorityTests {

        @Test
        @DisplayName("heavily penalised output has non-null review priority")
        void heavilyPenalisedOutputHasReviewPriority() {
            CompletionResult result = CompletionResult.builder()
                .text("Error: cannot generate. TODO FIXME")
                .modelUsed("gpt-4").build();
            CompletionRequest request = CompletionRequest.builder().prompt("generate").build();

            ConfidenceScore score = runPromise(() -> service.scoreCompletion(result, request));

            assertThat(score.getReviewPriority()).isNotNull();
        }

        @Test
        @DisplayName("clean well-formed output has non-null review priority")
        void cleanOutputHasReviewPriority() {
            CompletionResult result = CompletionResult.builder()
                .text("public class Service {\n  public void execute() {\n    doWork();\n  }\n}")
                .modelUsed("gpt-4").build();
            CompletionRequest request = CompletionRequest.builder().prompt("generate Java class").build();

            ConfidenceScore score = runPromise(() -> service.scoreCompletion(result, request));

            assertThat(score.getReviewPriority()).isNotNull();
        }
    }
}
