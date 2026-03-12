package com.ghatana.tutorputor.explorer.generator;

import com.ghatana.tutorputor.explorer.model.*;
import com.ghatana.tutorputor.agent.ContentQualityValidator;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type test
 * @doc.purpose Integration tests for ComprehensiveContentGenerator
 * @doc.layer test
 */
class ComprehensiveContentGeneratorIntegrationTest {

    private ComprehensiveContentGenerator generator;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        ContentQualityValidator qualityValidator = new ContentQualityValidator(meterRegistry);
        
        // Create generator with all dependencies
        generator = new ComprehensiveContentGenerator(
            new ClaimGenerator(),
            new EvidenceGenerator(),
            new ExampleGenerator(null, null, qualityValidator),
            new SimulationGenerator(),
            new AnimationGenerator(),
            new AssessmentGenerator(),
            null, // LLMGateway - would be mocked in real tests
            null, // EmbeddingService - would be mocked in real tests
            qualityValidator
        );
    }

    @Test
    @DisplayName("Should generate complete content package")
    void shouldGenerateCompleteContentPackage() throws Exception {
        // Given
        ContentGenerationRequest request = ContentGenerationRequest.builder()
            .topic("Newton's Laws of Motion")
            .gradeLevel("HIGH_SCHOOL")
            .domain(Domain.PHYSICS)
            .tenantId("tenant-test")
            .maxClaims(3)
            .maxExamples(5)
            .build();

        // When
        Promise<CompleteContentPackage> promise = generator.generateCompleteContent(request);
        CompleteContentPackage pkg = promise.get();

        // Then
        assertThat(pkg).isNotNull();
        assertThat(pkg.getClaims()).isNotEmpty();
        assertThat(pkg.getEvidence()).isNotEmpty();
        assertThat(pkg.getQualityReport()).isNotNull();
        assertThat(pkg.getGenerationDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should validate quality of generated content")
    void shouldValidateQualityOfGeneratedContent() throws Exception {
        // Given
        ContentGenerationRequest request = ContentGenerationRequest.builder()
            .topic("Test Topic")
            .gradeLevel("MIDDLE_SCHOOL")
            .domain(Domain.BIOLOGY)
            .tenantId("tenant-test")
            .build();

        // When
        CompleteContentPackage pkg = generator.generateCompleteContent(request).get();

        // Then
        assertThat(pkg.getQualityReport().getOverallScore()).isGreaterThanOrEqualTo(0.0);
        assertThat(pkg.getQualityReport().getOverallScore()).isLessThanOrEqualTo(1.0);
    }
}
