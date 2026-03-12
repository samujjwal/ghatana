package com.ghatana.tutorputor.contentgeneration;

import io.activej.promise.Promise;
import java.util.*;

/**
 * Port interface for content generation operations.
 *
 * <p><b>Purpose</b><br>
 * Defines contract for generating content based on prompts and context.
 * Supports streaming responses, batch generation, and model configuration.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ContentGenerator generator = new OpenAIContentGenerator(client, config);
 * PromptTemplate template = PromptTemplate.builder()
 *     .userPrompt("Summarize: {{text}}")
 *     .variable("text", "Lorem ipsum...")
 *     .build();
 * 
 * Promise<GenerationResult> result = generator.generateContent(
 *     "tenant-123", template, Map.of("model", "gpt-4")
 * );
 * }</pre>
 *
 * <p><b>Implementations</b><br>
 * - OpenAIContentGenerator: Uses OpenAI API (GPT-4, GPT-3.5)
 * - LocalContentGenerator: Uses local LLM (for testing)
 * - MockContentGenerator: Returns pre-configured responses (for testing)
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe.
 *
 * @doc.type interface
 * @doc.purpose Port for content generation operations
 * @doc.layer domain
 * @doc.pattern Port
 */
public interface ContentGenerator {

    /**
     * Generates content based on prompt template.
     *
     * <p>Resolves template variables, applies guardrails, and generates content.
     * Returns a Promise that completes with GenerationResult or fails with exception.
     *
     * @param tenantId tenant identifier (non-null)
     * @param template prompt template with variables (non-null)
     * @param guardrails generation constraints (non-null)
     * @param context additional context (key-value pairs, non-null)
     * @return Promise of generation result
     * @throws NullPointerException if any parameter is null
     */
    Promise<GenerationResult> generateContent(
            String tenantId,
            PromptTemplate template,
            GenerationGuardrails guardrails,
            Map<String, Object> context
    );

    /**
     * Generates content for multiple prompts in batch.
     *
     * <p>Parallel generation of multiple content items.
     * Returns Promise that completes with list of results in same order as input.
     *
     * @param tenantId tenant identifier (non-null)
     * @param templates prompt templates (non-null, non-empty)
     * @param guardrails generation constraints (non-null)
     * @param context additional context (non-null)
     * @return Promise of list of generation results
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if templates empty
     */
    Promise<List<GenerationResult>> generateBatch(
            String tenantId,
            List<PromptTemplate> templates,
            GenerationGuardrails guardrails,
            Map<String, Object> context
    );

    /**
     * Updates configuration for content generation.
     *
     * <p>Updates model selection, temperature, max tokens, etc.
     *
     * @param tenantId tenant identifier (non-null)
     * @param configuration configuration key-value pairs (non-null)
     * @return Promise of void (completes when configuration updated)
     * @throws NullPointerException if parameters null
     * @throws IllegalArgumentException if configuration invalid
     */
    Promise<Void> updateConfiguration(
            String tenantId,
            Map<String, Object> configuration
    );

    /**
     * Gets current configuration for tenant.
     *
     * @param tenantId tenant identifier (non-null)
     * @return Promise of configuration map
     * @throws NullPointerException if tenantId null
     */
    Promise<Map<String, Object>> getConfiguration(String tenantId);

    /**
     * Gets supported generation models.
     *
     * @return Promise of list of model names
     */
    Promise<List<String>> getSupportedModels();

    /**
     * Validates a prompt template without executing generation.
     *
     * <p>Checks template syntax, variable references, and compatibility.
     *
     * @param tenantId tenant identifier (non-null)
     * @param template template to validate (non-null)
     * @return Promise of validation result
     * @throws NullPointerException if parameters null
     */
    Promise<TemplateValidationResult> validateTemplate(
            String tenantId,
            PromptTemplate template
    );

    /**
     * Result of content generation.
     */
    final class GenerationResult {
        private final String generatedContent;
        private final String model;
        private final int inputTokens;
        private final int outputTokens;
        private final long generationTimeMillis;
        private final String finishReason;
        private final Map<String, Object> metadata;

        /**
         * Creates generation result.
         *
         * @param generatedContent generated text (non-null)
         * @param model model used (non-null)
         * @param inputTokens tokens in input
         * @param outputTokens tokens in output
         * @param generationTimeMillis generation time in milliseconds
         * @param finishReason reason generation stopped (non-null)
         * @param metadata additional metadata (non-null)
         * @throws NullPointerException if any non-numeric parameter null
         */
        public GenerationResult(
                String generatedContent,
                String model,
                int inputTokens,
                int outputTokens,
                long generationTimeMillis,
                String finishReason,
                Map<String, Object> metadata) {
            this.generatedContent = Objects.requireNonNull(generatedContent, "generatedContent cannot be null");
            this.model = Objects.requireNonNull(model, "model cannot be null");
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.generationTimeMillis = generationTimeMillis;
            this.finishReason = Objects.requireNonNull(finishReason, "finishReason cannot be null");
            this.metadata = Collections.unmodifiableMap(
                    new HashMap<>(Objects.requireNonNull(metadata, "metadata cannot be null"))
            );
        }

        public String getGeneratedContent() {
            return generatedContent;
        }

        public String getModel() {
            return model;
        }

        public int getInputTokens() {
            return inputTokens;
        }

        public int getOutputTokens() {
            return outputTokens;
        }

        public long getGenerationTimeMillis() {
            return generationTimeMillis;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        /**
         * Checks if generation completed successfully.
         *
         * @return true if finish reason is "stop"
         */
        public boolean isSuccess() {
            return "stop".equals(finishReason);
        }

        @Override
        public String toString() {
            return "GenerationResult{" +
                    "content=" + generatedContent.length() + " chars" +
                    ", model='" + model + '\'' +
                    ", tokens=" + inputTokens + "/" + outputTokens +
                    ", time=" + generationTimeMillis + "ms" +
                    ", reason='" + finishReason + '\'' +
                    '}';
        }
    }

    /**
     * Result of template validation.
     */
    final class TemplateValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final Set<String> referencedVariables;

        /**
         * Creates validation result.
         *
         * @param valid whether template is valid
         * @param errors validation error messages (non-null)
         * @param referencedVariables variables referenced in template (non-null)
         */
        public TemplateValidationResult(
                boolean valid,
                List<String> errors,
                Set<String> referencedVariables) {
            this.valid = valid;
            this.errors = Collections.unmodifiableList(
                    Objects.requireNonNull(errors, "errors cannot be null")
            );
            this.referencedVariables = Collections.unmodifiableSet(
                    Objects.requireNonNull(referencedVariables, "referencedVariables cannot be null")
            );
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public Set<String> getReferencedVariables() {
            return referencedVariables;
        }

        @Override
        public String toString() {
            return valid ? "VALID" : "INVALID: " + String.join(", ", errors);
        }
    }
}
