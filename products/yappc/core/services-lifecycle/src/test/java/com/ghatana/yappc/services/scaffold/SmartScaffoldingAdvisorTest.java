package com.ghatana.yappc.services.scaffold;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("SmartScaffoldingAdvisor Tests")
class SmartScaffoldingAdvisorTest extends EventloopTestBase {

    private CompletionService completionService = mock(CompletionService.class);

    private SmartScaffoldingAdvisor advisor;

    @BeforeEach
    void setUp() {
        advisor = new SmartScaffoldingAdvisor(completionService);
    }

    @Test
    @DisplayName("returns parsed recommendations sorted by confidence descending")
    void returnsParsedRecommendationsSortedByConfidence() {
        String llmResponse =
                "docker-compose-postgres|Docker Compose with PostgreSQL|0.80|Common backend setup\n" +
                "gradle-java-ci|GitHub Actions CI for Gradle Java|0.95|Matches detected Gradle+Java stack\n" +
                "java-checkstyle|Checkstyle code style config for Java|0.70|Enforces consistent code style\n";

        stubCompletionWith(llmResponse);

        List<SmartScaffoldingAdvisor.TemplateRecommendation> recs =
                runPromise(() -> advisor.recommendTemplates(javaProjectContext()));

        assertThat(recs).hasSize(3);
        // Sorted descending by confidence
        assertThat(recs.get(0).confidence()).isEqualTo(0.95);
        assertThat(recs.get(0).name()).isEqualTo("gradle-java-ci");
        assertThat(recs.get(1).confidence()).isEqualTo(0.80);
        assertThat(recs.get(2).confidence()).isEqualTo(0.70);
    }

    @Test
    @DisplayName("returns empty list when LLM response is blank")
    void returnsEmptyListForBlankResponse() {
        stubCompletionWith("   ");

        List<SmartScaffoldingAdvisor.TemplateRecommendation> recs =
                runPromise(() -> advisor.recommendTemplates(javaProjectContext()));

        assertThat(recs).isEmpty();
    }

    @Test
    @DisplayName("returns empty list for null context")
    void returnsEmptyListForNullContext() {
        List<SmartScaffoldingAdvisor.TemplateRecommendation> recs =
                runPromise(() -> advisor.recommendTemplates(null));

        assertThat(recs).isEmpty();
    }

    @Test
    @DisplayName("skips malformed lines missing pipe separators")
    void skipsMalformedLinesWithoutPipes() {
        String llmResponse =
                "This is a malformed line without pipes\n" +
                "gradle-java-ci|CI for Gradle Java|0.95|Matches the stack\n";

        stubCompletionWith(llmResponse);

        List<SmartScaffoldingAdvisor.TemplateRecommendation> recs =
                runPromise(() -> advisor.recommendTemplates(javaProjectContext()));

        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).name()).isEqualTo("gradle-java-ci");
    }

    @Test
    @DisplayName("skips lines with non-numeric confidence")
    void skipsLinesWithNonNumericConfidence() {
        String llmResponse =
                "gradle-java-ci|CI pipeline|high|Matches stack\n" +
                "docker-compose|Docker setup|0.85|Standard setup\n";

        stubCompletionWith(llmResponse);

        List<SmartScaffoldingAdvisor.TemplateRecommendation> recs =
                runPromise(() -> advisor.recommendTemplates(javaProjectContext()));

        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).name()).isEqualTo("docker-compose");
    }

    @Test
    @DisplayName("skips lines with confidence outside [0,1]")
    void skipsLinesWithConfidenceOutOfRange() {
        String llmResponse =
                "template-a|Description A|1.5|Too high confidence\n" +
                "template-b|Description B|-0.1|Negative confidence\n" +
                "template-c|Description C|0.85|Valid confidence\n";

        stubCompletionWith(llmResponse);

        List<SmartScaffoldingAdvisor.TemplateRecommendation> recs =
                runPromise(() -> advisor.recommendTemplates(javaProjectContext()));

        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).name()).isEqualTo("template-c");
    }

    @Test
    @DisplayName("respects maximum recommendation count of 5")
    void respectsMaxRecommendationCount() {
        StringBuilder llmResponse = new StringBuilder();
        for (int i = 1; i <= 8; i++) {
            llmResponse.append("template-").append(i).append("|Description ")
                    .append(i).append("|0.").append(50 + i).append("|Rationale ").append(i).append("\n");
        }

        stubCompletionWith(llmResponse.toString());

        List<SmartScaffoldingAdvisor.TemplateRecommendation> recs =
                runPromise(() -> advisor.recommendTemplates(javaProjectContext()));

        assertThat(recs).hasSize(5);
    }

    @Test
    @DisplayName("constructs with null completionService throws IllegalArgumentException")
    void constructorRejectsNullCompletionService() {
        assertThatThrownBy(() -> new SmartScaffoldingAdvisor(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completionService");
    }

    @Test
    @DisplayName("ProjectContext.of creates context with empty language and framework sets")
    void projectContextOfFactoryCreatesMinimalContext() {
        SmartScaffoldingAdvisor.ProjectContext ctx =
                SmartScaffoldingAdvisor.ProjectContext.of("A simple service", "Add logging config");

        assertThat(ctx.description()).isEqualTo("A simple service");
        assertThat(ctx.goal()).isEqualTo("Add logging config");
        assertThat(ctx.languages()).isEmpty();
        assertThat(ctx.frameworks()).isEmpty();
    }

    @Test
    @DisplayName("recommendation result is immutable")
    void recommendationListIsImmutable() {
        stubCompletionWith("gradle-java-ci|CI pipeline|0.95|Matches stack\n");

        List<SmartScaffoldingAdvisor.TemplateRecommendation> recs =
                runPromise(() -> advisor.recommendTemplates(javaProjectContext()));

        assertThat(recs).hasSize(1);
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> recs.add(new SmartScaffoldingAdvisor.TemplateRecommendation(
                        "new", "desc", 0.5, "test")));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private SmartScaffoldingAdvisor.ProjectContext javaProjectContext() {
        return new SmartScaffoldingAdvisor.ProjectContext(
                "Payment processing microservice",
                "Add CI/CD pipeline with security scanning",
                Set.of("java"),
                Set.of("gradle", "spring-boot")
        );
    }

    private void stubCompletionWith(String text) {
        CompletionResult result = CompletionResult.builder()
                .text(text)
                .tokensUsed(text.length() / 4)
                .build();
        when(completionService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(result));
    }
}
