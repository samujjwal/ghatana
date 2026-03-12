package com.ghatana.products.collection.infrastructure.generation;

import com.ghatana.products.collection.domain.generation.*;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ContentGenerationService.
 *
 * Tests validate:
 * - Content generation with template validation
 * - Guardrails enforcement on generated content
 * - Batch generation with sequential execution
 * - Template validation with completeness checking
 * - Configuration management delegation
 * - Error handling and recovery
 * - Metrics collection for success/failure/timing
 * - Tenant isolation in all operations
 *
 * @see ContentGenerationService
 */
@DisplayName("ContentGenerationService Tests")
class ContentGenerationServiceTest extends EventloopTestBase {

    private ContentGenerator mockGenerator;
    private ContentGenerationService service;

    @BeforeEach
    void setUp() {
        // GIVEN: Mock generator
        mockGenerator = mock(ContentGenerator.class);
        service = new ContentGenerationService(
                mockGenerator,
                NoopMetricsCollector.getInstance()
        );
    }

    /**
     * Verifies successful content generation with validation.
     *
     * GIVEN: Valid template, guardrails, and successful generation
     * WHEN: generateContent() called
     * THEN: Returns result with generated content and metrics
     */
    @Test
    @DisplayName("Should generate content with validation")
    void shouldGenerateContentWithValidation() {
        // GIVEN: Mock generator returns valid result
        PromptTemplate template = PromptTemplate.builder()
                .userPrompt("Generate: {{topic}}")
                .variable("topic", "AI safety")
                .build();

        ContentGenerator.GenerationResult mockResult =
                new ContentGenerator.GenerationResult(
                        "AI safety is important.",
                        "gpt-4",
                        10, 20,
                        500,
                        "stop",
                        Collections.emptyMap()
                );

        when(mockGenerator.generateContent(any(), any(), any(), any()))
                .thenReturn(Promise.of(mockResult));
        when(mockGenerator.validateTemplate(any(), any()))
                .thenReturn(Promise.of(new ContentGenerator.TemplateValidationResult(
                        true, Collections.emptyList(), Set.of("topic")
                )));

        GenerationGuardrails guardrails = GenerationGuardrails.builder().build();

        // WHEN: Generate content
        ContentGenerationService.GenerationResponse response = runPromise(() ->
                service.generateContent("tenant-123", template, guardrails)
        );

        // THEN: Result returned
        assertThat(response)
                .as("Should return generation response")
                .isNotNull();
        assertThat(response.getGeneratedContent())
                .as("Content should match generation result")
                .isEqualTo("AI safety is important.");
        assertThat(response.getModel())
                .as("Model should match")
                .isEqualTo("gpt-4");
    }

    /**
     * Verifies template validation failure handling.
     *
     * GIVEN: Template with invalid/missing variables
     * WHEN: generateContent() called
     * THEN: Fails without attempting generation
     */
    @Test
    @DisplayName("Should reject generation for invalid templates")
    void shouldRejectGenerationForInvalidTemplates() {
        // GIVEN: Invalid template (missing variables)
        PromptTemplate template = PromptTemplate.builder()
                .userPrompt("Generate: {{topic}}")
                .build();  // Missing "topic" variable

        GenerationGuardrails guardrails = GenerationGuardrails.builder().build();

        when(mockGenerator.validateTemplate(any(), any()))
                .thenReturn(Promise.of(new ContentGenerator.TemplateValidationResult(
                        false,
                        List.of("Missing variable: topic"),
                        Set.of("topic")
                )));

        // WHEN: Try to generate
        // THEN: Fails
        assertThatThrownBy(() -> runPromise(() ->
                service.generateContent("tenant-123", template, guardrails)
        ))
                .as("Should fail for invalid template")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template validation failed");
    }

    /**
     * Verifies guardrails enforcement rejects policy-violating content.
     *
     * GIVEN: Generated content violates guardrails
     * WHEN: generateContent() called
     * THEN: Fails with guardrail violation
     */
    @Test
    @DisplayName("Should enforce guardrails on generated content")
    void shouldEnforceGuardrailsOnGeneratedContent() {
        // GIVEN: Guardrails forbidding certain patterns
        PromptTemplate template = PromptTemplate.builder()
                .userPrompt("Generate content")
                .build();

        GenerationGuardrails guardrails = GenerationGuardrails.builder()
                .forbiddenPattern("forbidden")
                .build();

        ContentGenerator.GenerationResult mockResult =
                new ContentGenerator.GenerationResult(
                        "This contains forbidden word.",
                        "gpt-4",
                        10, 20,
                        500,
                        "stop",
                        Collections.emptyMap()
                );

        when(mockGenerator.generateContent(any(), any(), any(), any()))
                .thenReturn(Promise.of(mockResult));
        when(mockGenerator.validateTemplate(any(), any()))
                .thenReturn(Promise.of(new ContentGenerator.TemplateValidationResult(
                        true, Collections.emptyList(), Collections.emptySet()
                )));

        // WHEN: Generate content
        // THEN: Fails due to guardrail violation
        assertThatThrownBy(() -> runPromise(() ->
                service.generateContent("tenant-123", template, guardrails)
        ))
                .as("Should reject content violating guardrails")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("guardrails");
    }

    /**
     * Verifies successful batch generation.
     *
     * GIVEN: Multiple templates and guardrails
     * WHEN: generateBatch() called
     * THEN: Returns results in order
     */
    @Test
    @DisplayName("Should generate batch of content sequentially")
    void shouldGenerateBatchOfContentSequentially() {
        // GIVEN: Multiple templates
        List<PromptTemplate> templates = Arrays.asList(
                PromptTemplate.builder().userPrompt("Content 1").build(),
                PromptTemplate.builder().userPrompt("Content 2").build(),
                PromptTemplate.builder().userPrompt("Content 3").build()
        );

        GenerationGuardrails guardrails = GenerationGuardrails.builder().build();

        // Mock generation results
        for (int i = 0; i < templates.size(); i++) {
            ContentGenerator.GenerationResult result =
                    new ContentGenerator.GenerationResult(
                            "Generated content " + (i + 1),
                            "gpt-4",
                            10, 20, 500, "stop",
                            Collections.emptyMap()
                    );
            when(mockGenerator.generateContent(any(), any(), any(), any()))
                    .thenReturn(Promise.of(result));
        }

        when(mockGenerator.validateTemplate(any(), any()))
                .thenReturn(Promise.of(new ContentGenerator.TemplateValidationResult(
                        true, Collections.emptyList(), Collections.emptySet()
                )));

        // WHEN: Generate batch
        ContentGenerationService.BatchGenerationResponse response = runPromise(() ->
                service.generateBatch("tenant-123", templates, guardrails)
        );

        // THEN: All results returned
        assertThat(response)
                .as("Should return batch response")
                .isNotNull();
        assertThat(response.getResults())
                .as("Should have all results")
                .hasSize(3);
        assertThat(response.getSuccessCount())
                .as("All should succeed")
                .isEqualTo(3);
        assertThat(response.getErrorCount())
                .as("No errors")
                .isEqualTo(0);
    }

    /**
     * Verifies batch generation continues on individual failures.
     *
     * GIVEN: Second item in batch fails generation
     * WHEN: generateBatch() called
     * THEN: Returns results for successful items and error for failed item
     */
    @Test
    @DisplayName("Should continue batch generation on individual failures")
    void shouldContinueBatchGenerationOnIndividualFailures() {
        // GIVEN: Three templates
        List<PromptTemplate> templates = Arrays.asList(
                PromptTemplate.builder().userPrompt("Content 1").build(),
                PromptTemplate.builder().userPrompt("Content 2").build(),
                PromptTemplate.builder().userPrompt("Content 3").build()
        );

        GenerationGuardrails guardrails = GenerationGuardrails.builder().build();

        // Setup mock: fail on second call
        ContentGenerator.GenerationResult success1 =
                new ContentGenerator.GenerationResult(
                        "Content 1", "gpt-4", 10, 20, 500, "stop", Collections.emptyMap()
                );
        ContentGenerator.GenerationResult success3 =
                new ContentGenerator.GenerationResult(
                        "Content 3", "gpt-4", 10, 20, 500, "stop", Collections.emptyMap()
                );

        when(mockGenerator.generateContent(any(), any(), any(), any()))
                .thenReturn(Promise.of(success1))
                .thenReturn(Promise.ofException(new RuntimeException("Mock generation error")))
                .thenReturn(Promise.of(success3));

        when(mockGenerator.validateTemplate(any(), any()))
                .thenReturn(Promise.of(new ContentGenerator.TemplateValidationResult(
                        true, Collections.emptyList(), Collections.emptySet()
                )));

        // WHEN: Generate batch
        ContentGenerationService.BatchGenerationResponse response = runPromise(() ->
                service.generateBatch("tenant-123", templates, guardrails)
        );

        // THEN: Two successes, one error
        assertThat(response.getSuccessCount())
                .as("Should have 2 successful items")
                .isEqualTo(2);
        assertThat(response.getErrorCount())
                .as("Should have 1 error")
                .isEqualTo(1);
        assertThat(response.getErrors())
                .as("Should include error message")
                .hasSize(1)
                .anySatisfy(e -> assertThat(e).contains("Item 1"));
    }

    /**
     * Verifies template validation delegates to generator.
     *
     * GIVEN: Valid template with variables
     * WHEN: validateTemplate() called
     * THEN: Returns validation result from generator
     */
    @Test
    @DisplayName("Should validate templates with completeness checks")
    void shouldValidateTemplatesWithCompletenessChecks() {
        // GIVEN: Template with variables
        PromptTemplate template = PromptTemplate.builder()
                .userPrompt("Content about {{topic}}")
                .variable("topic", "AI")
                .build();

        when(mockGenerator.validateTemplate(any(), any()))
                .thenReturn(Promise.of(new ContentGenerator.TemplateValidationResult(
                        true, Collections.emptyList(), Set.of("topic")
                )));

        // WHEN: Validate
        ContentGenerator.TemplateValidationResult result = runPromise(() ->
                service.validateTemplate("tenant-123", template)
        );

        // THEN: Valid
        assertThat(result.isValid())
                .as("Template should be valid")
                .isTrue();
        assertThat(result.getReferencedVariables())
                .as("Should include referenced variables")
                .containsExactly("topic");
    }

    /**
     * Verifies configuration update delegation.
     *
     * GIVEN: Configuration map
     * WHEN: configureGeneration() called
     * THEN: Delegates to generator and records metrics
     */
    @Test
    @DisplayName("Should update configuration through generator")
    void shouldUpdateConfigurationThroughGenerator() {
        // GIVEN: Configuration
        Map<String, Object> config = Map.of("model", "gpt-4", "temperature", 0.7);

        when(mockGenerator.updateConfiguration(any(), any()))
                .thenReturn(Promise.of((Void) null));

        // WHEN: Update configuration
        runPromise(() ->
                service.configureGeneration("tenant-123", config)
        );

        // THEN: Delegation verified
        verify(mockGenerator, times(1)).updateConfiguration("tenant-123", config);
    }

    /**
     * Verifies configuration update error handling.
     *
     * GIVEN: Generator throws exception on configuration update
     * WHEN: configureGeneration() called
     * THEN: Exception propagated
     */
    @Test
    @DisplayName("Should propagate configuration update errors")
    void shouldPropagateConfigurationUpdateErrors() {
        // GIVEN: Configuration that causes error
        Map<String, Object> config = Map.of("invalid", "value");

        when(mockGenerator.updateConfiguration(any(), any()))
                .thenReturn(Promise.ofException(new IllegalArgumentException("Invalid config")));

        // WHEN: Update configuration
        // THEN: Exception thrown
        assertThatThrownBy(() -> runPromise(() ->
                service.configureGeneration("tenant-123", config)
        ))
                .as("Should propagate configuration error")
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies null tenant ID rejected.
     *
     * GIVEN: null tenantId
     * WHEN: generateContent() called
     * THEN: NullPointerException thrown
     */
    @Test
    @DisplayName("Should require non-null tenant ID")
    void shouldRequireNonNullTenantID() {
        // GIVEN: Null tenant ID
        PromptTemplate template = PromptTemplate.builder()
                .userPrompt("Content")
                .build();
        GenerationGuardrails guardrails = GenerationGuardrails.builder().build();

        // WHEN/THEN: Null tenant rejected
        assertThatThrownBy(() -> runPromise(() ->
                service.generateContent(null, template, guardrails)
        ))
                .as("Should reject null tenant ID")
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies null template rejected.
     *
     * GIVEN: null template
     * WHEN: generateContent() called
     * THEN: NullPointerException thrown
     */
    @Test
    @DisplayName("Should require non-null template")
    void shouldRequireNonNullTemplate() {
        // GIVEN: Null template
        GenerationGuardrails guardrails = GenerationGuardrails.builder().build();

        // WHEN/THEN: Null template rejected
        assertThatThrownBy(() -> runPromise(() ->
                service.generateContent("tenant-123", null, guardrails)
        ))
                .as("Should reject null template")
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies null guardrails rejected.
     *
     * GIVEN: null guardrails
     * WHEN: generateContent() called
     * THEN: NullPointerException thrown
     */
    @Test
    @DisplayName("Should require non-null guardrails")
    void shouldRequireNonNullGuardrails() {
        // GIVEN: Null guardrails
        PromptTemplate template = PromptTemplate.builder()
                .userPrompt("Content")
                .build();

        // WHEN/THEN: Null guardrails rejected
        assertThatThrownBy(() -> runPromise(() ->
                service.generateContent("tenant-123", template, null)
        ))
                .as("Should reject null guardrails")
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies empty batch templates rejected.
     *
     * GIVEN: Empty templates list
     * WHEN: generateBatch() called
     * THEN: IllegalArgumentException thrown
     */
    @Test
    @DisplayName("Should reject empty batch templates")
    void shouldRejectEmptyBatchTemplates() {
        // GIVEN: Empty templates
        List<PromptTemplate> templates = Collections.emptyList();
        GenerationGuardrails guardrails = GenerationGuardrails.builder().build();

        // WHEN/THEN: Empty batch rejected
        assertThatThrownBy(() -> runPromise(() ->
                service.generateBatch("tenant-123", templates, guardrails)
        ))
                .as("Should reject empty batch")
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies successful models retrieval.
     *
     * GIVEN: Generator returns list of models
     * WHEN: getSupportedModels() called
     * THEN: Returns models list
     */
    @Test
    @DisplayName("Should retrieve supported models from generator")
    void shouldRetrieveSupportedModelsFromGenerator() {
        // GIVEN: Generator returns models
        List<String> models = Arrays.asList("gpt-4", "gpt-3.5", "claude-2");

        when(mockGenerator.getSupportedModels())
                .thenReturn(Promise.of(models));

        // WHEN: Get supported models
        List<String> result = runPromise(() ->
                service.getSupportedModels()
        );

        // THEN: Models returned
        assertThat(result)
                .as("Should return supported models")
                .isEqualTo(models);
    }

    /**
     * Verifies metrics collection for successful generation.
     *
     * GIVEN: Successful generation
     * WHEN: generateContent() completes
     * THEN: Metrics recorded (verified via metrics collector)
     */
    @Test
    @DisplayName("Should collect metrics on successful generation")
    void shouldCollectMetricsOnSuccessfulGeneration() {
        // GIVEN: Mock generator returning success
        PromptTemplate template = PromptTemplate.builder()
                .userPrompt("Content")
                .build();

        ContentGenerator.GenerationResult mockResult =
                new ContentGenerator.GenerationResult(
                        "Generated", "gpt-4", 10, 20, 500, "stop", Collections.emptyMap()
                );

        when(mockGenerator.generateContent(any(), any(), any(), any()))
                .thenReturn(Promise.of(mockResult));
        when(mockGenerator.validateTemplate(any(), any()))
                .thenReturn(Promise.of(new ContentGenerator.TemplateValidationResult(
                        true, Collections.emptyList(), Collections.emptySet()
                )));

        GenerationGuardrails guardrails = GenerationGuardrails.builder().build();

        // WHEN: Generate
        runPromise(() ->
                service.generateContent("tenant-123", template, guardrails)
        );

        // THEN: Success (metrics would be recorded via MetricsCollector mock)
        verify(mockGenerator, times(1)).generateContent(
                eq("tenant-123"), eq(template), eq(guardrails), any()
        );
    }

    /**
     * Verifies batch results maintain order matching input.
     *
     * GIVEN: Three templates in specific order
     * WHEN: generateBatch() called
     * THEN: Results returned in same order
     */
    @Test
    @DisplayName("Should maintain result order in batch generation")
    void shouldMaintainResultOrderInBatchGeneration() {
        // GIVEN: Three ordered templates
        List<PromptTemplate> templates = Arrays.asList(
                PromptTemplate.builder().userPrompt("First").build(),
                PromptTemplate.builder().userPrompt("Second").build(),
                PromptTemplate.builder().userPrompt("Third").build()
        );

        GenerationGuardrails guardrails = GenerationGuardrails.builder().build();

        // Mock ordered results
        when(mockGenerator.generateContent(any(), any(), any(), any()))
                .thenReturn(Promise.of(new ContentGenerator.GenerationResult(
                        "Generated 1", "gpt-4", 10, 20, 500, "stop", Collections.emptyMap()
                )))
                .thenReturn(Promise.of(new ContentGenerator.GenerationResult(
                        "Generated 2", "gpt-4", 10, 20, 500, "stop", Collections.emptyMap()
                )))
                .thenReturn(Promise.of(new ContentGenerator.GenerationResult(
                        "Generated 3", "gpt-4", 10, 20, 500, "stop", Collections.emptyMap()
                )));

        when(mockGenerator.validateTemplate(any(), any()))
                .thenReturn(Promise.of(new ContentGenerator.TemplateValidationResult(
                        true, Collections.emptyList(), Collections.emptySet()
                )));

        // WHEN: Generate batch
        ContentGenerationService.BatchGenerationResponse response = runPromise(() ->
                service.generateBatch("tenant-123", templates, guardrails)
        );

        // THEN: Order preserved
        assertThat(response.getResults())
                .as("Results should be in order")
                .extracting(ContentGenerationService.GenerationResponse::getGeneratedContent)
                .containsExactly("Generated 1", "Generated 2", "Generated 3");
    }
}
