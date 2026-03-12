package com.ghatana.tutorputor.contentgeneration;

import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.tutorputor.contentgeneration.domain.*;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the unified content generation service.
 *
 * <p>Tests end-to-end workflows including content generation, validation,
 * and platform service integration.
 *
 * @doc.type test
 * @doc.purpose Integration testing for content generation workflows
 * @doc.layer test
 */
@DisplayName("Content Generation Integration Tests")
public class ContentGenerationIntegrationTest {

    private PlatformContentGenerator contentGenerator;
    private MockLLMGateway mockLLMGateway;
    private ContentValidator validator;
    private PromptTemplateEngine promptEngine;
    private SimpleMetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        mockLLMGateway = new MockLLMGateway();
        validator = new ContentValidator();
        promptEngine = new PromptTemplateEngine();
        metricsCollector = new SimpleMetricsCollector();
        
        contentGenerator = new PlatformContentGenerator(
            mockLLMGateway,
            validator,
            promptEngine,
            metricsCollector
        );
    }

    @Test
    @DisplayName("Should generate complete content package successfully")
    void shouldGenerateCompleteContentPackage() {
        // Given
        ContentGenerationRequest request = ContentGenerationRequest.builder()
            .topic("Newton's Laws of Motion")
            .gradeLevel("HIGH_SCHOOL")
            .domain("PHYSICS")
            .tenantId("test-tenant")
            .maxClaims(3)
            .maxExamples(2)
            .maxSimulations(1)
            .maxAnimations(1)
            .maxAssessments(3)
            .build();

        // When
        Promise<CompleteContentPackage> resultPromise = contentGenerator.generateCompletePackage(request);
        CompleteContentPackage result = resultPromise.get();

        // Then
        assertNotNull(result, "Result should not be null");
        assertNotNull(result.getClaims(), "Claims should not be null");
        assertNotNull(result.getExamples(), "Examples should not be null");
        assertNotNull(result.getSimulations(), "Simulations should not be null");
        assertNotNull(result.getAnimations(), "Animations should not be null");
        assertNotNull(result.getAssessments(), "Assessments should not be null");
        assertNotNull(result.getQualityReport(), "Quality report should not be null");
    }

    @Test
    @DisplayName("Should generate claims for a topic")
    void shouldGenerateClaims() {
        // Given
        ClaimsRequest request = new ClaimsRequest(
            "Photosynthesis",
            "MIDDLE_SCHOOL",
            "BIOLOGY",
            5
        );

        // When
        Promise<ClaimsResponse> resultPromise = contentGenerator.generateClaims(request);
        ClaimsResponse result = resultPromise.get();

        // Then
        assertNotNull(result, "Result should not be null");
        assertNotNull(result.getClaims(), "Claims list should not be null");
        assertNotNull(result.getValidation(), "Validation should not be null");
        assertFalse(result.getClaims().isEmpty(), "Should generate at least one claim");
    }

    @Test
    @DisplayName("Should validate generated content")
    void shouldValidateGeneratedContent() {
        // Given
        List<LearningClaim> claims = List.of(
            new LearningClaim("claim-1", "Objects in motion stay in motion", "HIGH_SCHOOL", "PHYSICS", Map.of()),
            new LearningClaim("claim-2", "F = ma", "HIGH_SCHOOL", "PHYSICS", Map.of())
        );

        // When
        Promise<ValidationResult> validationPromise = contentGenerator.validateContent(claims, ContentType.CLAIMS);
        ValidationResult result = validationPromise.get();

        // Then
        assertNotNull(result, "Validation result should not be null");
        assertTrue(result.getConfidence() >= 0.0 && result.getConfidence() <= 1.0, 
            "Confidence should be between 0 and 1");
    }

    @Test
    @DisplayName("Should return supported models")
    void shouldReturnSupportedModels() {
        // When
        Promise<List<String>> modelsPromise = contentGenerator.getSupportedModels();
        List<String> models = modelsPromise.get();

        // Then
        assertNotNull(models, "Models list should not be null");
        assertFalse(models.isEmpty(), "Should have at least one supported model");
    }

    @Test
    @DisplayName("Should return configuration")
    void shouldReturnConfiguration() {
        // When
        Promise<Map<String, Object>> configPromise = contentGenerator.getConfiguration("test-tenant");
        Map<String, Object> config = configPromise.get();

        // Then
        assertNotNull(config, "Configuration should not be null");
        assertTrue(config.containsKey("defaultModel"), "Should contain defaultModel");
        assertTrue(config.containsKey("temperature"), "Should contain temperature");
        assertTrue(config.containsKey("availableProviders"), "Should contain availableProviders");
    }

    @Test
    @DisplayName("Should handle empty claims gracefully")
    void shouldHandleEmptyClaims() {
        // Given
        ContentGenerationRequest request = ContentGenerationRequest.builder()
            .topic("Empty Topic")
            .gradeLevel("HIGH_SCHOOL")
            .domain("PHYSICS")
            .tenantId("test-tenant")
            .maxClaims(0)
            .maxExamples(0)
            .build();

        // When
        Promise<CompleteContentPackage> resultPromise = contentGenerator.generateCompletePackage(request);
        CompleteContentPackage result = resultPromise.get();

        // Then
        assertNotNull(result, "Result should not be null");
        assertTrue(result.getClaims().isEmpty(), "Should have no claims");
    }

    @Test
    @DisplayName("Should track metrics during generation")
    void shouldTrackMetrics() {
        // Given
        ContentGenerationRequest request = ContentGenerationRequest.builder()
            .topic("Chemical Reactions")
            .gradeLevel("HIGH_SCHOOL")
            .domain("CHEMISTRY")
            .tenantId("test-tenant")
            .build();

        // When
        contentGenerator.generateCompletePackage(request).get();

        // Then
        assertTrue(metricsCollector.getTimerCount() > 0, "Should have recorded timing metrics");
        assertTrue(metricsCollector.hasTimer("content.generation.complete_package"), 
            "Should track complete package generation");
    }

    /**
     * Mock LLM Gateway for testing.
     */
    private static class MockLLMGateway implements LLMGateway {
        
        @Override
        public Promise<CompletionResult> complete(CompletionRequest request) {
            return Promise.of(new CompletionResult(
                "Generated content for: " + request.getPrompt(),
                "gpt-4",
                100,
                50,
                System.currentTimeMillis(),
                "stop",
                Map.of()
            ));
        }

        @Override
        public Promise<CompletionResult> completeWithTools(CompletionRequest request, List tools) {
            return complete(request);
        }

        @Override
        public Promise<CompletionResult> continueWithToolResults(CompletionRequest request, List toolResults) {
            return complete(request);
        }

        @Override
        public Promise embed(String text) {
            return Promise.of(null);
        }

        @Override
        public Promise stream(CompletionRequest request) {
            return Promise.of(null);
        }

        @Override
        public Promise embedBatch(List texts) {
            return Promise.of(Collections.emptyList());
        }

        @Override
        public MetricsCollector getMetrics() {
            return new SimpleMetricsCollector();
        }

        @Override
        public String getDefaultProvider() {
            return "mock";
        }

        @Override
        public List<String> getAvailableProviders() {
            return List.of("mock", "openai", "anthropic");
        }

        @Override
        public boolean isProviderAvailable(String providerName) {
            return true;
        }
    }

    /**
     * Simple metrics collector for testing.
     */
    private static class SimpleMetricsCollector implements MetricsCollector {
        private final Map<String, Integer> counters = new HashMap<>();
        private final Map<String, List<Long>> timers = new HashMap<>();

        @Override
        public void incrementCounter(String name, String... tags) {
            counters.merge(name, 1, Integer::sum);
        }

        @Override
        public void recordTimer(String name, long durationMs, String... tags) {
            timers.computeIfAbsent(name, k -> new ArrayList<>()).add(durationMs);
        }

        @Override
        public void recordGauge(String name, double value, String... tags) {
            // No-op for testing
        }

        public int getTimerCount() {
            return timers.values().stream().mapToInt(List::size).sum();
        }

        public boolean hasTimer(String name) {
            return timers.containsKey(name);
        }
    }
}
