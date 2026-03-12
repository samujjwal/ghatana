package com.ghatana.tutorputor.contentgeneration;

import com.ghatana.tutorputor.contentgeneration.*;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import java.util.*;

/**
 * Application service orchestrating content generation operations.
 *
 * <p><b>Purpose</b><br>
 * Coordinates content generation with guardrails enforcement, template validation,
 * and metrics collection. Routes generation to appropriate implementation based on
 * tenant configuration.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ContentGenerationService service = new ContentGenerationService(
 *     generator, metrics
 * );
 * 
 * PromptTemplate template = PromptTemplate.builder()
 *     .userPrompt("Summarize: {{text}}")
 *     .variable("text", "...")
 *     .build();
 * 
 * GenerationGuardrails guardrails = GenerationGuardrails.builder()
 *     .maxLength(1000)
 *     .build();
 * 
 * Promise<ContentGenerationService.GenerationResponse> response =
 *     service.generateContent("tenant-123", template, guardrails);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Application service orchestrating domain ContentGenerator port with infrastructure:
 * - Template validation before generation
 * - Guardrails enforcement on output
 * - Metrics collection and reporting
 * - Error aggregation and recovery
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe if underlying generator is thread-safe.
 *
 * @doc.type class
 * @doc.purpose Application service for content generation orchestration
 * @doc.layer infrastructure
 * @doc.pattern Service
 */
public final class ContentGenerationService {

    private final ContentGenerator contentGenerator;
    private final MetricsCollector metricsCollector;

    /**
     * Creates ContentGenerationService.
     *
     * @param contentGenerator content generator implementation (non-null)
     * @param metricsCollector metrics collector (non-null)
     * @throws NullPointerException if any parameter null
     */
    public ContentGenerationService(
            ContentGenerator contentGenerator,
            MetricsCollector metricsCollector) {
        this.contentGenerator = Objects.requireNonNull(
                contentGenerator, "contentGenerator cannot be null"
        );
        this.metricsCollector = Objects.requireNonNull(
                metricsCollector, "metricsCollector cannot be null"
        );
    }

    /**
     * Generates content with validation and guardrails.
     *
     * <p>GIVEN: Valid tenant, template, and guardrails
     * WHEN: generateContent() called
     * THEN: Validates template, generates content, enforces guardrails, records metrics
     *
     * @param tenantId tenant identifier (non-null)
     * @param template prompt template (non-null)
     * @param guardrails generation constraints (non-null)
     * @return Promise of generation response with result and metrics
     * @throws NullPointerException if any parameter null
     */
    public Promise<GenerationResponse> generateContent(
            String tenantId,
            PromptTemplate template,
            GenerationGuardrails guardrails) {
        
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(guardrails, "guardrails cannot be null");

        return validateTemplate(tenantId, template)
                .then(validationResult -> {
                    if (!validationResult.isValid()) {
                        metricsCollector.incrementCounter(
                                "generation.validation.failures",
                                "tenant", tenantId
                        );
                        return Promise.ofException(
                                new IllegalArgumentException(
                                        "Template validation failed: " + 
                                        String.join(", ", validationResult.getErrors())
                                )
                        );
                    }

                    // Template valid, proceed to generation
                    Map<String, Object> context = new HashMap<>();
                    context.put("guardrails", guardrails);
                    
                    return contentGenerator.generateContent(
                            tenantId, template, guardrails, context
                    );
                })
                .then(generationResult -> {
                    // Validate guardrails
                    GenerationGuardrails.GuardrailValidationResult guardrailResult =
                            guardrails.validate(generationResult.getGeneratedContent());
                    
                    if (!guardrailResult.isValid()) {
                        metricsCollector.incrementCounter(
                                "generation.guardrails.violations",
                                "tenant", tenantId,
                                "violations", String.valueOf(guardrailResult.getViolations().size())
                        );
                        return Promise.ofException(
                                new IllegalStateException(
                                        "Generated content violates guardrails: " +
                                        String.join(", ", guardrailResult.getViolations())
                                )
                        );
                    }

                    // Record success metrics
                    metricsCollector.incrementCounter(
                            "generation.success",
                            "tenant", tenantId,
                            "model", generationResult.getModel()
                    );
                    metricsCollector.recordTimer(
                            "generation.time",
                            generationResult.getGenerationTimeMillis(),
                            "tenant", tenantId
                    );

                    return Promise.of(new GenerationResponse(
                            generationResult.getGeneratedContent(),
                            generationResult.getModel(),
                            generationResult.getInputTokens(),
                            generationResult.getOutputTokens(),
                            generationResult.getGenerationTimeMillis(),
                            Collections.emptyList()
                    ));
                })
                .catchEx(exception -> {
                    metricsCollector.incrementCounter(
                            "generation.errors",
                            "tenant", tenantId,
                            "error_type", exception.getClass().getSimpleName()
                    );
                    return Promise.ofException(exception);
                });
    }

    /**
     * Generates content for multiple prompts in batch.
     *
     * <p>Sequential generation with aggregated results and metrics.
     *
     * @param tenantId tenant identifier (non-null)
     * @param templates prompt templates (non-null, non-empty)
     * @param guardrails generation constraints (non-null)
     * @return Promise of batch response with results in order
     * @throws NullPointerException if any parameter null
     * @throws IllegalArgumentException if templates empty
     */
    public Promise<BatchGenerationResponse> generateBatch(
            String tenantId,
            List<PromptTemplate> templates,
            GenerationGuardrails guardrails) {
        
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(templates, "templates cannot be null");
        Objects.requireNonNull(guardrails, "guardrails cannot be null");
        
        if (templates.isEmpty()) {
            throw new IllegalArgumentException("templates cannot be empty");
        }

        List<GenerationResponse> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        return generateBatchRecursive(
                tenantId, templates, guardrails, 0, results, errors
        ).then(unused -> {
            metricsCollector.incrementCounter(
                    "generation.batch.complete",
                    "tenant", tenantId,
                    "count", String.valueOf(templates.size()),
                    "errors", String.valueOf(errors.size())
            );

            return Promise.of(new BatchGenerationResponse(results, errors));
        });
    }

    /**
     * Configures generation model and parameters for tenant.
     *
     * @param tenantId tenant identifier (non-null)
     * @param configuration configuration parameters (non-null)
     * @return Promise of void
     * @throws NullPointerException if parameters null
     */
    public Promise<Void> configureGeneration(
            String tenantId,
            Map<String, Object> configuration) {
        
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(configuration, "configuration cannot be null");

        return contentGenerator.updateConfiguration(tenantId, configuration)
                .then(unused -> {
                    metricsCollector.incrementCounter(
                            "generation.configuration.updated",
                            "tenant", tenantId,
                            "params", String.valueOf(configuration.size())
                    );
                    return Promise.of((Void) null);
                })
                .catchEx(exception -> {
                    metricsCollector.incrementCounter(
                            "generation.configuration.errors",
                            "tenant", tenantId
                    );
                    return Promise.ofException(exception);
                });
    }

    /**
     * Gets supported generation models.
     *
     * @return Promise of list of model names
     */
    public Promise<List<String>> getSupportedModels() {
        return contentGenerator.getSupportedModels();
    }

    /**
     * Validates template syntax and variables.
     *
     * <p>GIVEN: Valid tenant and template
     * WHEN: validateTemplate() called
     * THEN: Returns validation result with errors (if any) and referenced variables
     *
     * @param tenantId tenant identifier (non-null)
     * @param template prompt template (non-null)
     * @return Promise of template validation result
     * @throws NullPointerException if parameters null
     */
    public Promise<ContentGenerator.TemplateValidationResult> validateTemplate(
            String tenantId,
            PromptTemplate template) {
        
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(template, "template cannot be null");

        List<String> errors = new ArrayList<>();

        // Check template completeness
        if (!template.isComplete()) {
            Set<String> missing = template.getMissingVariables();
            errors.add("Missing variables: " + String.join(", ", missing));
        }

        // Check prompt lengths
        String userPrompt = template.resolveUserPrompt();
        if (userPrompt.length() > 10000) {
            errors.add("User prompt exceeds 10,000 characters");
        }

        String systemPrompt = template.resolveSystemPrompt();
        if (systemPrompt != null && systemPrompt.length() > 5000) {
            errors.add("System prompt exceeds 5,000 characters");
        }

        return contentGenerator.validateTemplate(tenantId, template)
                .map(generatorResult -> {
                    // Merge errors from generator
                    errors.addAll(generatorResult.getErrors());
                    return new ContentGenerator.TemplateValidationResult(
                            errors.isEmpty(),
                            errors,
                            template.getReferencedVariables()
                    );
                });
    }

    /**
     * Recursively generates content for batch.
     *
     * @param tenantId tenant identifier
     * @param templates templates to process
     * @param guardrails generation constraints
     * @param index current index
     * @param results accumulated results
     * @param errors accumulated errors
     * @return Promise of void when all processed
     */
    private Promise<Void> generateBatchRecursive(
            String tenantId,
            List<PromptTemplate> templates,
            GenerationGuardrails guardrails,
            int index,
            List<GenerationResponse> results,
            List<String> errors) {
        
        if (index >= templates.size()) {
            return Promise.of((Void) null);
        }

        return generateContent(tenantId, templates.get(index), guardrails)
                .then(response -> {
                    results.add(response);
                    return generateBatchRecursive(
                            tenantId, templates, guardrails, index + 1, results, errors
                    );
                })
                .catchEx(exception -> {
                    errors.add("Item " + index + ": " + exception.getMessage());
                    return generateBatchRecursive(
                            tenantId, templates, guardrails, index + 1, results, errors
                    );
                });
    }

    /**
     * Response from content generation.
     */
    public static final class GenerationResponse {
        private final String generatedContent;
        private final String model;
        private final int inputTokens;
        private final int outputTokens;
        private final long generationTimeMillis;
        private final List<String> warnings;

        /**
         * Creates generation response.
         *
         * @param generatedContent generated text (non-null)
         * @param model model used (non-null)
         * @param inputTokens tokens in input
         * @param outputTokens tokens in output
         * @param generationTimeMillis generation time in milliseconds
         * @param warnings any warnings during generation (non-null)
         */
        public GenerationResponse(
                String generatedContent,
                String model,
                int inputTokens,
                int outputTokens,
                long generationTimeMillis,
                List<String> warnings) {
            this.generatedContent = Objects.requireNonNull(
                    generatedContent, "generatedContent cannot be null"
            );
            this.model = Objects.requireNonNull(model, "model cannot be null");
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.generationTimeMillis = generationTimeMillis;
            this.warnings = Collections.unmodifiableList(
                    new ArrayList<>(Objects.requireNonNull(warnings, "warnings cannot be null"))
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

        public List<String> getWarnings() {
            return warnings;
        }

        @Override
        public String toString() {
            return "GenerationResponse{" +
                    "content=" + generatedContent.length() + " chars" +
                    ", model='" + model + '\'' +
                    ", tokens=" + inputTokens + "/" + outputTokens +
                    ", time=" + generationTimeMillis + "ms" +
                    '}';
        }
    }

    /**
     * Response from batch generation.
     */
    public static final class BatchGenerationResponse {
        private final List<GenerationResponse> results;
        private final List<String> errors;

        /**
         * Creates batch response.
         *
         * @param results generation results (non-null)
         * @param errors error messages (non-null)
         */
        public BatchGenerationResponse(
                List<GenerationResponse> results,
                List<String> errors) {
            this.results = Collections.unmodifiableList(
                    new ArrayList<>(Objects.requireNonNull(results, "results cannot be null"))
            );
            this.errors = Collections.unmodifiableList(
                    new ArrayList<>(Objects.requireNonNull(errors, "errors cannot be null"))
            );
        }

        public List<GenerationResponse> getResults() {
            return results;
        }

        public List<String> getErrors() {
            return errors;
        }

        public int getSuccessCount() {
            return results.size();
        }

        public int getErrorCount() {
            return errors.size();
        }

        @Override
        public String toString() {
            return "BatchGenerationResponse{" +
                    "success=" + results.size() +
                    ", errors=" + errors.size() +
                    '}';
        }
    }
}
