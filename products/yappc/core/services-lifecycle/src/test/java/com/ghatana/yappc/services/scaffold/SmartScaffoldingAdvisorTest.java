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

@DisplayName("SmartScaffoldingAdvisor Tests [GH-90000]")
class SmartScaffoldingAdvisorTest extends EventloopTestBase {

    private CompletionService completionService = mock(CompletionService.class); // GH-90000

    private SmartScaffoldingAdvisor advisor;

    @BeforeEach
    void setUp() { // GH-90000
        advisor = new SmartScaffoldingAdvisor(completionService); // GH-90000
    }

    @Test
    @DisplayName("returns parsed recommendations sorted by confidence descending [GH-90000]")
    void returnsParsedRecommendationsSortedByConfidence() { // GH-90000
        String llmResponse =
                "docker-compose-postgres|Docker Compose with PostgreSQL|0.80|Common backend setup\n" +
                "gradle-java-ci|GitHub Actions CI for Gradle Java|0.95|Matches detected Gradle+Java stack\n" +
                "java-checkstyle|Checkstyle code style config for Java|0.70|Enforces consistent code style\n";

        stubCompletionWith(llmResponse); // GH-90000

        List<SmartScaffoldingAdvisor.TemplateRecommendation> recs =
                runPromise(() -> advisor.recommendTemplates(javaProjectContext())); // GH-90000

        assertThat(recs).hasSize(3); // GH-90000
        // Sorted descending by confidence
        assertThat(recs.get(0).confidence()).isEqualTo(0.95); // GH-90000
        assertThat(recs.get(0).name()).isEqualTo("gradle-java-ci [GH-90000]");
        assertThat(recs.get(1).confidence()).isEqualTo(0.80); // GH-90000
        assertThat(recs.get(2).confidence()).isEqualTo(0.70); // GH-90000
    }

    @Test
    @DisplayName("returns empty list when LLM response is blank [GH-90000]")
    void returnsEmptyListForBlankResponse() { // GH-90000
        stubCompletionWith("    [GH-90000]");

        List<SmartScaffoldingAdvisor.TemplateRecommendation> recs =
                runPromise(() -> advisor.recommendTemplates(javaProjectContext())); // GH-90000

        assertThat(recs).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("returns empty list for null context [GH-90000]")
    void returnsEmptyListForNullContext() { // GH-90000
        List<SmartScaffoldingAdvisor.TemplateRecommendation> recs =
                runPromise(() -> advisor.recommendTemplates(null)); // GH-90000

        assertThat(recs).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("skips malformed lines missing pipe separators [GH-90000]")
    void skipsMalformedLinesWithoutPipes() { // GH-90000
        String llmResponse =
                "This is a malformed line without pipes\n" +
                "gradle-java-ci|CI for Gradle Java|0.95|Matches the stack\n";

        stubCompletionWith(llmResponse); // GH-90000

        List<SmartScaffoldingAdvisor.TemplateRecommendation> recs =
                runPromise(() -> advisor.recommendTemplates(javaProjectContext())); // GH-90000

        assertThat(recs).hasSize(1); // GH-90000
        assertThat(recs.get(0).name()).isEqualTo("gradle-java-ci [GH-90000]");
    }

    @Test
    @DisplayName("skips lines with non-numeric confidence [GH-90000]")
    void skipsLinesWithNonNumericConfidence() { // GH-90000
        String llmResponse =
                "gradle-java-ci|CI pipeline|high|Matches stack\n" +
                "docker-compose|Docker setup|0.85|Standard setup\n";

        stubCompletionWith(llmResponse); // GH-90000

        List<SmartScaffoldingAdvisor.TemplateRecommendation> recs =
                runPromise(() -> advisor.recommendTemplates(javaProjectContext())); // GH-90000

        assertThat(recs).hasSize(1); // GH-90000
        assertThat(recs.get(0).name()).isEqualTo("docker-compose [GH-90000]");
    }

    @Test
    @DisplayName("skips lines with confidence outside [0,1] [GH-90000]")
    void skipsLinesWithConfidenceOutOfRange() { // GH-90000
        String llmResponse =
                "template-a|Description A|1.5|Too high confidence\n" +
                "template-b|Description B|-0.1|Negative confidence\n" +
                "template-c|Description C|0.85|Valid confidence\n";

        stubCompletionWith(llmResponse); // GH-90000

        List<SmartScaffoldingAdvisor.TemplateRecommendation> recs =
                runPromise(() -> advisor.recommendTemplates(javaProjectContext())); // GH-90000

        assertThat(recs).hasSize(1); // GH-90000
        assertThat(recs.get(0).name()).isEqualTo("template-c [GH-90000]");
    }

    @Test
    @DisplayName("respects maximum recommendation count of 5 [GH-90000]")
    void respectsMaxRecommendationCount() { // GH-90000
        StringBuilder llmResponse = new StringBuilder(); // GH-90000
        for (int i = 1; i <= 8; i++) { // GH-90000
            llmResponse.append("template- [GH-90000]").append(i).append("|Description  [GH-90000]")
                    .append(i).append("|0. [GH-90000]").append(50 + i).append("|Rationale  [GH-90000]").append(i).append("\n [GH-90000]");
        }

        stubCompletionWith(llmResponse.toString()); // GH-90000

        List<SmartScaffoldingAdvisor.TemplateRecommendation> recs =
                runPromise(() -> advisor.recommendTemplates(javaProjectContext())); // GH-90000

        assertThat(recs).hasSize(5); // GH-90000
    }

    @Test
    @DisplayName("constructs with null completionService throws IllegalArgumentException [GH-90000]")
    void constructorRejectsNullCompletionService() { // GH-90000
        assertThatThrownBy(() -> new SmartScaffoldingAdvisor(null)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("completionService [GH-90000]");
    }

    @Test
    @DisplayName("ProjectContext.of creates context with empty language and framework sets [GH-90000]")
    void projectContextOfFactoryCreatesMinimalContext() { // GH-90000
        SmartScaffoldingAdvisor.ProjectContext ctx =
                SmartScaffoldingAdvisor.ProjectContext.of("A simple service", "Add logging config"); // GH-90000

        assertThat(ctx.description()).isEqualTo("A simple service [GH-90000]");
        assertThat(ctx.goal()).isEqualTo("Add logging config [GH-90000]");
        assertThat(ctx.languages()).isEmpty(); // GH-90000
        assertThat(ctx.frameworks()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("recommendation result is immutable [GH-90000]")
    void recommendationListIsImmutable() { // GH-90000
        stubCompletionWith("gradle-java-ci|CI pipeline|0.95|Matches stack\n [GH-90000]");

        List<SmartScaffoldingAdvisor.TemplateRecommendation> recs =
                runPromise(() -> advisor.recommendTemplates(javaProjectContext())); // GH-90000

        assertThat(recs).hasSize(1); // GH-90000
        org.junit.jupiter.api.Assertions.assertThrows( // GH-90000
                UnsupportedOperationException.class,
                () -> recs.add(new SmartScaffoldingAdvisor.TemplateRecommendation( // GH-90000
                        "new", "desc", 0.5, "test")));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private SmartScaffoldingAdvisor.ProjectContext javaProjectContext() { // GH-90000
        return new SmartScaffoldingAdvisor.ProjectContext( // GH-90000
                "Payment processing microservice",
                "Add CI/CD pipeline with security scanning",
                Set.of("java [GH-90000]"),
                Set.of("gradle", "spring-boot") // GH-90000
        );
    }

    private void stubCompletionWith(String text) { // GH-90000
        CompletionResult result = CompletionResult.builder() // GH-90000
                .text(text) // GH-90000
                .tokensUsed(text.length() / 4) // GH-90000
                .build(); // GH-90000
        when(completionService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000
    }
}
