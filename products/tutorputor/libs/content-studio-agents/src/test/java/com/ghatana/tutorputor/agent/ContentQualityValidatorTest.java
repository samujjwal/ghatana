package com.ghatana.tutorputor.agent;

import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ContentQualityValidator.
 *
 * @doc.type test
 * @doc.purpose Tests for content quality validation
 * @doc.layer product
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContentQualityValidator Tests")
class ContentQualityValidatorTest extends EventloopTestBase {

    @Mock
    private AgentContext mockContext;

    private ContentQualityValidator validator;

    @Test
    @DisplayName("Should pass validation for good claim content")
    void shouldPassValidationForGoodClaimContent() {
        // Given
        validator = new ContentQualityValidator(new SimpleMeterRegistry());
        ContentGenerationResponse response = ContentGenerationResponse.builder()
            .content("Photosynthesis is the process by which green plants and some other organisms use sunlight to synthesize foods from carbon dioxide and water.")
            .domain("SCIENCE")
            .gradeLevel("6")
            .contentType(ContentGenerationRequest.ContentType.CLAIM)
            .qualityScore(0.8)
            .generationTimeMs(100)
            .tokenCount(30)
            .build();

        // When
        ContentQualityValidator.ValidationResult result =
            runPromise(() -> validator.validate(response, mockContext));

        // Then
        assertThat(result.passed()).isTrue();
        assertThat(result.score()).isGreaterThan(0.8);
        assertThat(result.issues()).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation for too short content")
    void shouldFailValidationForTooShortContent() {
        // Given
        validator = new ContentQualityValidator(new SimpleMeterRegistry());
        ContentGenerationResponse response = ContentGenerationResponse.builder()
            .content("Short")
            .domain("SCIENCE")
            .gradeLevel("6")
            .contentType(ContentGenerationRequest.ContentType.CLAIM)
            .qualityScore(0.5)
            .generationTimeMs(50)
            .tokenCount(5)
            .build();

        // When
        ContentQualityValidator.ValidationResult result =
            runPromise(() -> validator.validate(response, mockContext));

        // Then
        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.contains("too short"));
    }

    @Test
    @DisplayName("Should fail validation for content with placeholders")
    void shouldFailValidationForContentWithPlaceholders() {
        // Given
        validator = new ContentQualityValidator(new SimpleMeterRegistry());
        ContentGenerationResponse response = ContentGenerationResponse.builder()
            .content("This is a learning claim about [TODO] that students should understand.")
            .domain("SCIENCE")
            .gradeLevel("6")
            .contentType(ContentGenerationRequest.ContentType.CLAIM)
            .qualityScore(0.5)
            .generationTimeMs(100)
            .tokenCount(20)
            .build();

        // When
        ContentQualityValidator.ValidationResult result =
            runPromise(() -> validator.validate(response, mockContext));

        // Then
        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.contains("placeholder"));
    }

    @Test
    @DisplayName("Should fail validation for inappropriate language")
    void shouldFailValidationForInappropriateLanguage() {
        // Given
        validator = new ContentQualityValidator(new SimpleMeterRegistry());
        ContentGenerationResponse response = ContentGenerationResponse.builder()
            .content("This is a damn good explanation of how photosynthesis works in plants and other organisms.")
            .domain("SCIENCE")
            .gradeLevel("6")
            .contentType(ContentGenerationRequest.ContentType.CLAIM)
            .qualityScore(0.5)
            .generationTimeMs(100)
            .tokenCount(20)
            .build();

        // When
        ContentQualityValidator.ValidationResult result =
            runPromise(() -> validator.validate(response, mockContext));

        // Then
        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.contains("inappropriate"));
    }

    @Test
    @DisplayName("Should fail validation for complex vocabulary in elementary grades")
    void shouldFailValidationForComplexVocabularyInElementaryGrades() {
        // Given
        validator = new ContentQualityValidator(new SimpleMeterRegistry());
        ContentGenerationResponse response = ContentGenerationResponse.builder()
            .content("The epistemological framework suggests that knowledge acquisition through direct observation is fundamental to understanding natural phenomena.")
            .domain("SCIENCE")
            .gradeLevel("3")
            .contentType(ContentGenerationRequest.ContentType.CLAIM)
            .qualityScore(0.5)
            .generationTimeMs(100)
            .tokenCount(25)
            .build();

        // When
        ContentQualityValidator.ValidationResult result =
            runPromise(() -> validator.validate(response, mockContext));

        // Then
        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.contains("complex") || issue.contains("vocabulary"));
    }

    @Test
    @DisplayName("Should pass validation for simulation JSON content")
    void shouldPassValidationForSimulationJsonContent() {
        // Given
                validator = new ContentQualityValidator(new SimpleMeterRegistry());
        String simulationJson = """
            {
              "type": "physics-simulation",
              "entities": [
                {"id": "ball", "position": {"x": 0, "y": 10}}
              ],
              "steps": [
                {"time": 0, "description": "Ball at rest"}
              ]
            }
            """;
        
        ContentGenerationResponse response = ContentGenerationResponse.builder()
            .content(simulationJson)
            .domain("PHYSICS")
            .gradeLevel("9")
            .contentType(ContentGenerationRequest.ContentType.SIMULATION)
            .qualityScore(0.8)
            .generationTimeMs(200)
            .tokenCount(50)
            .build();

        // When
        ContentQualityValidator.ValidationResult result =
            runPromise(() -> validator.validate(response, mockContext));

        // Then
        assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("Should fail validation for simulation with non-JSON content")
    void shouldFailValidationForSimulationWithNonJsonContent() {
        // Given
        validator = new ContentQualityValidator(new SimpleMeterRegistry());
        ContentGenerationResponse response = ContentGenerationResponse.builder()
            .content("This is a plain text description of a physics simulation showing a ball falling due to gravity.")
            .domain("PHYSICS")
            .gradeLevel("9")
            .contentType(ContentGenerationRequest.ContentType.SIMULATION)
            .qualityScore(0.5)
            .generationTimeMs(100)
            .tokenCount(20)
            .build();

        // When
        ContentQualityValidator.ValidationResult result =
            runPromise(() -> validator.validate(response, mockContext));

        // Then
        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.contains("JSON"));
    }

    @Test
    @DisplayName("Should fail validation for overly verbose claims")
    void shouldFailValidationForOverlyVerboseClaims() {
        // Given - claim with too many sentences
        validator = new ContentQualityValidator(new SimpleMeterRegistry());
        String verboseClaim = """
            Plants make their own food through photosynthesis. 
            This process uses sunlight as energy. 
            Carbon dioxide comes from the air.
            Water is absorbed through the roots.
            The plant produces glucose and oxygen.
            """;
        
        ContentGenerationResponse response = ContentGenerationResponse.builder()
            .content(verboseClaim)
            .domain("SCIENCE")
            .gradeLevel("6")
            .contentType(ContentGenerationRequest.ContentType.CLAIM)
            .qualityScore(0.5)
            .generationTimeMs(100)
            .tokenCount(40)
            .build();

        // When
        ContentQualityValidator.ValidationResult result =
            runPromise(() -> validator.validate(response, mockContext));

        // Then
        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.contains("concise"));
    }

    @Test
    @DisplayName("Should handle null content gracefully")
    void shouldHandleNullContentGracefully() {
        // Given
        validator = new ContentQualityValidator(new SimpleMeterRegistry());
        ContentGenerationResponse response = ContentGenerationResponse.builder()
            .content(null)
            .domain("SCIENCE")
            .gradeLevel("6")
            .contentType(ContentGenerationRequest.ContentType.CLAIM)
            .qualityScore(0.0)
            .generationTimeMs(0)
            .tokenCount(0)
            .build();

        // When
        ContentQualityValidator.ValidationResult result =
            runPromise(() -> validator.validate(response, mockContext));

        // Then
        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> 
            issue.contains("null") || issue.contains("empty"));
    }

    @Test
    @DisplayName("Should accept content for higher grades with complex vocabulary")
    void shouldAcceptContentForHigherGradesWithComplexVocabulary() {
        // Given - same complex content but for college level
        validator = new ContentQualityValidator(new SimpleMeterRegistry());
        ContentGenerationResponse response = ContentGenerationResponse.builder()
            .content("The epistemological framework suggests that knowledge acquisition through direct observation is fundamental to understanding natural phenomena in scientific inquiry.")
            .domain("SCIENCE")
            .gradeLevel("college")
            .contentType(ContentGenerationRequest.ContentType.CLAIM)
            .qualityScore(0.8)
            .generationTimeMs(100)
            .tokenCount(25)
            .build();

        // When
        ContentQualityValidator.ValidationResult result =
            runPromise(() -> validator.validate(response, mockContext));

        // Then
        assertThat(result.passed()).isTrue();
        assertThat(result.issues()).isEmpty();
    }
}
