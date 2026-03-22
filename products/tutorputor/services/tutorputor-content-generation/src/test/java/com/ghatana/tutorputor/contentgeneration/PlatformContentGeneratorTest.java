package com.ghatana.tutorputor.contentgeneration;

import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.tutorputor.contentgeneration.domain.ContentGenerationRequest;
import com.ghatana.tutorputor.contentgeneration.domain.Domain;
import com.ghatana.tutorputor.contentgeneration.domain.UnifiedContentGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Platform Content Generator Tests")
class PlatformContentGeneratorTest extends EventloopTestBase {

    @Test
    @DisplayName("Should generate a complete package from canonical domain models")
    void shouldGenerateCompletePackageFromCanonicalDomainModels() {
        PlatformContentGenerator generator = new PlatformContentGenerator(
                mock(LLMGateway.class),
                new ContentValidator(),
                new PromptTemplateEngine(),
                NoopMetricsCollector.getInstance()
        );
        ContentGenerationRequest request = ContentGenerationRequest.builder()
                .topic("Newtonian mechanics")
                .gradeLevel("GRADE_8")
                .domain(Domain.PHYSICS)
                .tenantId("tenant-a")
                .maxClaims(2)
                .maxExamples(2)
                .maxSimulations(1)
                .maxAnimations(1)
                .maxAssessments(1)
                .build();

        UnifiedContentGenerator.CompleteContentPackage result =
                runPromise(() -> generator.generateCompletePackage(request));

        assertThat(result.claims()).hasSize(2);
        assertThat(result.examples()).hasSize(2);
        assertThat(result.simulations()).hasSize(1);
        assertThat(result.animations()).hasSize(1);
        assertThat(result.assessments()).hasSize(1);
        assertThat(result.qualityReport().isPassed()).isTrue();
        assertThat(result.qualityReport().getOverallScore()).isEqualTo(1.0);
    }
}