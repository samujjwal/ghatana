package com.ghatana.products.collection.infrastructure.generation;

import com.ghatana.products.collection.domain.generation.*;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for MockContentGenerator.
 *
 * Tests validate:
 * - Configurable response generation
 * - Tenant-specific responses
 * - Model and token configuration
 * - Batch generation
 * - Error simulation
 * - Configuration management
 * - Template validation
 *
 * @see MockContentGenerator
 */
@DisplayName("MockContentGenerator Tests")
class MockContentGeneratorTest extends EventloopTestBase {

    private MockContentGenerator generator;

    @BeforeEach
    void setUp() {
        // GIVEN: Mock generator with defaults
        generator = MockContentGenerator.builder()
                .respondWith("Mock generated content")
                .model("mock-gpt-4")
                .tokenCost(100, 200)
                .build();
    }

    /**
     * Verifies basic content generation with default response.
     *
     * GIVEN: Mock generator configured with default response
     * WHEN: generateContent() called
     * THEN: Returns configured response
     */
    @Test
    @DisplayName("Should generate content with configured response")
    void shouldGenerateContentWithConfiguredResponse() {
        // GIVEN: Generator configured
        PromptTemplate template = PromptTemplate.builder()
                .userPrompt("Generate content")
                .build();
        GenerationGuardrails guardrails = GenerationGuardrails.builder().build();

        // WHEN: Generate
        ContentGenerator.GenerationResult result = runPromise(() ->
                generator.generateContent("tenant-123", template, guardrails, Map.of())
        );

        // THEN: Response matches configuration
        assertThat(result.getGeneratedContent())
                .as("Should return configured response")
                .isEqualTo("Mock generated content");
        assertThat(result.getModel())
                .as("Model should match configuration")
                .isEqualTo("mock-gpt-4");
        assertThat(result.getInputTokens())
                .as("Input tokens should match")
                .isEqualTo(100);
        assertThat(result.getOutputTokens())
                .as("Output tokens should match")
                .isEqualTo(200);
    }

    /**
     * Verifies tenant-specific responses override default.
     *
     * GIVEN: Generator configured with tenant-specific response
     * WHEN: generateContent() called for that tenant
     * THEN: Returns tenant-specific response
     */
    @Test
    @DisplayName("Should return tenant-specific responses")
    void shouldReturnTenantSpecificResponses() {
        // GIVEN: Tenant-specific response configured
        generator = MockContentGenerator.builder()
                .respondWith("Default response")
                .respondForTenant("tenant-123", "Tenant-specific response")
                .respondForTenant("tenant-456", "Another tenant response")
                .build();

        PromptTemplate template = PromptTemplate.builder()
                .userPrompt("Generate")
                .build();
        GenerationGuardrails guardrails = GenerationGuardrails.builder().build();

        // WHEN: Generate for specific tenants
        ContentGenerator.GenerationResult result1 = runPromise(() ->
                generator.generateContent("tenant-123", template, guardrails, Map.of())
        );
        ContentGenerator.GenerationResult result2 = runPromise(() ->
                generator.generateContent("tenant-456", template, guardrails, Map.of())
        );
        ContentGenerator.GenerationResult resultDefault = runPromise(() ->
                generator.generateContent("unknown-tenant", template, guardrails, Map.of())
        );

        // THEN: Correct responses returned
        assertThat(result1.getGeneratedContent())
                .isEqualTo("Tenant-specific response");
        assertThat(result2.getGeneratedContent())
                .isEqualTo("Another tenant response");
        assertThat(resultDefault.getGeneratedContent())
                .isEqualTo("Default response");
    }

    /**
     * Verifies batch generation with same response for all items.
     *
     * GIVEN: Batch of templates
     * WHEN: generateBatch() called
     * THEN: Returns results with indexed content
     */
    @Test
    @DisplayName("Should generate batch with indexed responses")
    void shouldGenerateBatchWithIndexedResponses() {
        // GIVEN: Three templates
        List<PromptTemplate> templates = Arrays.asList(
                PromptTemplate.builder().userPrompt("Prompt 1").build(),
                PromptTemplate.builder().userPrompt("Prompt 2").build(),
                PromptTemplate.builder().userPrompt("Prompt 3").build()
        );
        GenerationGuardrails guardrails = GenerationGuardrails.builder().build();

        // WHEN: Generate batch
        List<ContentGenerator.GenerationResult> results = runPromise(() ->
                generator.generateBatch("tenant-123", templates, guardrails, Map.of())
        );

        // THEN: Results for each item
        assertThat(results)
                .as("Should have result for each template")
                .hasSize(3);
        assertThat(results.get(0).getGeneratedContent())
                .as("First result should have index 1")
                .contains("Item 1");
        assertThat(results.get(1).getGeneratedContent())
                .as("Second result should have index 2")
                .contains("Item 2");
        assertThat(results.get(2).getGeneratedContent())
                .as("Third result should have index 3")
                .contains("Item 3");
    }

    /**
     * Verifies error simulation capability.
     *
     * GIVEN: Generator configured to throw error
     * WHEN: generateContent() called
     * THEN: Throws configured exception
     */
    @Test
    @DisplayName("Should simulate generation errors")
    void shouldSimulateGenerationErrors() {
        // GIVEN: Generator configured to throw
        Exception testError = new RuntimeException("Mock generation error");
        generator = MockContentGenerator.builder()
                .throwError(testError)
                .build();

        PromptTemplate template = PromptTemplate.builder()
                .userPrompt("Generate")
                .build();
        GenerationGuardrails guardrails = GenerationGuardrails.builder().build();

        // WHEN/THEN: Generation throws error
        assertThatThrownBy(() -> runPromise(() ->
                generator.generateContent("tenant-123", template, guardrails, Map.of())
        ))
                .as("Should throw simulated error")
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Mock generation error");
    }

    /**
     * Verifies batch error simulation.
     *
     * GIVEN: Generator configured to throw in batch
     * WHEN: generateBatch() called
     * THEN: Throws error
     */
    @Test
    @DisplayName("Should simulate batch generation errors")
    void shouldSimulateBatchGenerationErrors() {
        // GIVEN: Generator configured to throw in batch
        generator = MockContentGenerator.builder()
                .throwError(new RuntimeException("Batch error"))
                .build();

        List<PromptTemplate> templates = Arrays.asList(
                PromptTemplate.builder().userPrompt("P1").build(),
                PromptTemplate.builder().userPrompt("P2").build()
        );
        GenerationGuardrails guardrails = GenerationGuardrails.builder().build();

        // WHEN/THEN: Batch generation throws
        assertThatThrownBy(() -> runPromise(() ->
                generator.generateBatch("tenant-123", templates, guardrails, Map.of())
        ))
                .as("Should throw batch error")
                .isInstanceOf(RuntimeException.class);
    }

    /**
     * Verifies configuration update succeeds silently.
     *
     * GIVEN: Configuration update request
     * WHEN: updateConfiguration() called
     * THEN: Completes successfully
     */
    @Test
    @DisplayName("Should handle configuration updates silently")
    void shouldHandleConfigurationUpdatesSilently() {
        // GIVEN: Configuration
        Map<String, Object> config = Map.of("model", "custom-model", "temp", 0.5);

        // WHEN: Update configuration
        runPromise(() ->
                generator.updateConfiguration("tenant-123", config)
        );

        // THEN: Completes without error
        assertThat(true).isTrue();
    }

    /**
     * Verifies configuration retrieval.
     *
     * GIVEN: Generator with configured parameters
     * WHEN: getConfiguration() called
     * THEN: Returns configuration
     */
    @Test
    @DisplayName("Should retrieve configuration")
    void shouldRetrieveConfiguration() {
        // WHEN: Get configuration
        Map<String, Object> config = runPromise(() ->
                generator.getConfiguration("tenant-123")
        );

        // THEN: Configuration returned
        assertThat(config)
                .as("Should return configuration map")
                .containsEntry("model", "mock-gpt-4")
                .containsEntry("inputTokens", 100)
                .containsEntry("outputTokens", 200);
    }

    /**
     * Verifies supported models list.
     *
     * GIVEN: Generator configured with models
     * WHEN: getSupportedModels() called
     * THEN: Returns list of models
     */
    @Test
    @DisplayName("Should return supported models")
    void shouldReturnSupportedModels() {
        // GIVEN: Generator with configured models
        generator = MockContentGenerator.builder()
                .supportedModels("gpt-4", "gpt-3.5", "claude-2")
                .build();

        // WHEN: Get supported models
        List<String> models = runPromise(() ->
                generator.getSupportedModels()
        );

        // THEN: Models returned
        assertThat(models)
                .as("Should return supported models")
                .containsExactlyInAnyOrder("gpt-4", "gpt-3.5", "claude-2");
    }

    /**
     * Verifies template validation.
     *
     * GIVEN: Valid template
     * WHEN: validateTemplate() called
     * THEN: Returns validation result
     */
    @Test
    @DisplayName("Should validate templates")
    void shouldValidateTemplates() {
        // GIVEN: Valid template
        PromptTemplate template = PromptTemplate.builder()
                .userPrompt("Content about {{topic}}")
                .variable("topic", "AI")
                .build();

        // WHEN: Validate
        ContentGenerator.TemplateValidationResult result = runPromise(() ->
                generator.validateTemplate("tenant-123", template)
        );

        // THEN: Validation result returned
        assertThat(result.isValid())
                .as("Template should be valid")
                .isTrue();
        assertThat(result.getReferencedVariables())
                .as("Should contain referenced variables")
                .containsExactly("topic");
    }

    /**
     * Verifies template validation with missing variables.
     *
     * GIVEN: Template with missing variables
     * WHEN: validateTemplate() called
     * THEN: Returns invalid result with error
     */
    @Test
    @DisplayName("Should detect missing variables in templates")
    void shouldDetectMissingVariablesInTemplates() {
        // GIVEN: Template with missing variable
        PromptTemplate template = PromptTemplate.builder()
                .userPrompt("Content about {{topic}}")
                .build();  // Missing "topic" variable

        // WHEN: Validate
        ContentGenerator.TemplateValidationResult result = runPromise(() ->
                generator.validateTemplate("tenant-123", template)
        );

        // THEN: Invalid with error
        assertThat(result.isValid())
                .as("Template should be invalid")
                .isFalse();
        assertThat(result.getErrors())
                .as("Should have error about missing variables")
                .hasSize(1)
                .anySatisfy(e -> assertThat(e).contains("Missing variables"));
    }

    /**
     * Verifies generation time parameter.
     *
     * GIVEN: Generator configured with specific generation time
     * WHEN: generateContent() called
     * THEN: Result reports configured time
     */
    @Test
    @DisplayName("Should report configured generation time")
    void shouldReportConfiguredGenerationTime() {
        // GIVEN: Generator with specific timing
        generator = MockContentGenerator.builder()
                .respondWith("Content")
                .generationTime(1234)
                .build();

        PromptTemplate template = PromptTemplate.builder()
                .userPrompt("Generate")
                .build();
        GenerationGuardrails guardrails = GenerationGuardrails.builder().build();

        // WHEN: Generate
        ContentGenerator.GenerationResult result = runPromise(() ->
                generator.generateContent("tenant-123", template, guardrails, Map.of())
        );

        // THEN: Time matches configuration
        assertThat(result.getGenerationTimeMillis())
                .as("Should report configured generation time")
                .isEqualTo(1234);
    }

    /**
     * Verifies finish reason is "stop".
     *
     * GIVEN: Mock generator
     * WHEN: generateContent() called
     * THEN: Result has finish reason "stop"
     */
    @Test
    @DisplayName("Should report finish reason as stop")
    void shouldReportFinishReasonAsStop() {
        // GIVEN: Mock generator
        PromptTemplate template = PromptTemplate.builder()
                .userPrompt("Generate")
                .build();
        GenerationGuardrails guardrails = GenerationGuardrails.builder().build();

        // WHEN: Generate
        ContentGenerator.GenerationResult result = runPromise(() ->
                generator.generateContent("tenant-123", template, guardrails, Map.of())
        );

        // THEN: Finish reason is stop
        assertThat(result.getFinishReason())
                .as("Should report finish reason")
                .isEqualTo("stop");
        assertThat(result.isSuccess())
                .as("Should indicate success")
                .isTrue();
    }

    /**
     * Verifies setTenantResponse() method for dynamic configuration.
     *
     * GIVEN: Generator and tenant-specific response set dynamically
     * WHEN: generateContent() called
     * THEN: Returns dynamically set response
     */
    @Test
    @DisplayName("Should support dynamic tenant response configuration")
    void shouldSupportDynamicTenantResponseConfiguration() {
        // GIVEN: Default generator
        // WHEN: Set response dynamically
        generator.setTenantResponse("dynamic-tenant", "Dynamically set response");

        PromptTemplate template = PromptTemplate.builder()
                .userPrompt("Generate")
                .build();
        GenerationGuardrails guardrails = GenerationGuardrails.builder().build();

        // WHEN: Generate with dynamic tenant
        ContentGenerator.GenerationResult result = runPromise(() ->
                generator.generateContent("dynamic-tenant", template, guardrails, Map.of())
        );

        // THEN: Dynamically set response returned
        assertThat(result.getGeneratedContent())
                .isEqualTo("Dynamically set response");
    }

    /**
     * Verifies getTenantResponse() retrieval method.
     *
     * GIVEN: Generator with tenant-specific response
     * WHEN: getTenantResponse() called
     * THEN: Returns configured response
     */
    @Test
    @DisplayName("Should retrieve configured tenant response")
    void shouldRetrieveConfiguredTenantResponse() {
        // GIVEN: Tenant response set
        generator.setTenantResponse("tenant-123", "Configured response");

        // WHEN: Retrieve
        String response = generator.getTenantResponse("tenant-123");

        // THEN: Response returned
        assertThat(response)
                .as("Should return configured response")
                .isEqualTo("Configured response");
    }

    /**
     * Verifies getTenantResponse() returns null for unconfigured tenant.
     *
     * GIVEN: Tenant without specific response configured
     * WHEN: getTenantResponse() called
     * THEN: Returns null
     */
    @Test
    @DisplayName("Should return null for unconfigured tenant")
    void shouldReturnNullForUnconfiguredTenant() {
        // WHEN: Retrieve unconfigured tenant
        String response = generator.getTenantResponse("unknown-tenant");

        // THEN: Null returned
        assertThat(response)
                .as("Should return null for unconfigured tenant")
                .isNull();
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
        // WHEN/THEN: Null template rejected
        assertThatThrownBy(() -> runPromise(() ->
                generator.generateContent("tenant-123", null, GenerationGuardrails.builder().build(), Map.of())
        ))
                .as("Should reject null template")
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
        // WHEN/THEN: Empty batch rejected
        assertThatThrownBy(() -> runPromise(() ->
                generator.generateBatch("tenant-123", Collections.emptyList(), GenerationGuardrails.builder().build(), Map.of())
        ))
                .as("Should reject empty batch")
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies metadata in generation result.
     *
     * GIVEN: Batch generation
     * WHEN: generateBatch() called
     * THEN: Each result has metadata with index
     */
    @Test
    @DisplayName("Should include index in batch result metadata")
    void shouldIncludeIndexInBatchResultMetadata() {
        // GIVEN: Batch of templates
        List<PromptTemplate> templates = Arrays.asList(
                PromptTemplate.builder().userPrompt("P1").build(),
                PromptTemplate.builder().userPrompt("P2").build()
        );

        // WHEN: Generate batch
        List<ContentGenerator.GenerationResult> results = runPromise(() ->
                generator.generateBatch("tenant-123", templates, GenerationGuardrails.builder().build(), Map.of())
        );

        // THEN: Metadata contains index
        assertThat(results.get(0).getMetadata())
                .as("First result should have index 0")
                .containsEntry("index", 0);
        assertThat(results.get(1).getMetadata())
                .as("Second result should have index 1")
                .containsEntry("index", 1);
    }
}
