package com.ghatana.products.collection.infrastructure.generation;

import com.ghatana.products.collection.domain.generation.*;
import io.activej.promise.Promise;
import java.util.*;

/**
 * Mock ContentGenerator implementation for testing.
 *
 * <p><b>Purpose</b><br>
 * Provides predictable, configurable responses for testing content generation
 * and orchestration logic without external dependencies.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MockContentGenerator generator = MockContentGenerator.builder()
 *     .respondWith("Hello, world!")
 *     .model("mock-gpt-4")
 *     .tokenCost(10, 20)
 *     .build();
 * 
 * Promise<ContentGenerator.GenerationResult> result = 
 *     generator.generateContent("tenant-123", template, guardrails, Map.of());
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Configurable responses per tenantId
 * - Configurable model, tokens, timing
 * - Simulates errors and validation failures
 * - Thread-safe configuration
 *
 * @doc.type class
 * @doc.purpose Mock content generator for testing
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public final class MockContentGenerator implements ContentGenerator {

    private final Map<String, String> responsesByTenant;
    private final String defaultResponse;
    private final String model;
    private final int inputTokens;
    private final int outputTokens;
    private final long generationTimeMillis;
    private final Map<String, Object> supportedModels;
    private final boolean throwError;
    private final Exception errorToThrow;

    /**
     * Creates MockContentGenerator.
     *
     * @param responsesByTenant per-tenant responses (non-null)
     * @param defaultResponse default response for unmapped tenants (non-null)
     * @param model model name (non-null)
     * @param inputTokens input token count
     * @param outputTokens output token count
     * @param generationTimeMillis generation time simulation in ms
     * @param supportedModels list of supported models (non-null)
     * @param throwError whether to throw exception on generation
     * @param errorToThrow exception to throw if throwError true (can be null)
     */
    private MockContentGenerator(
            Map<String, String> responsesByTenant,
            String defaultResponse,
            String model,
            int inputTokens,
            int outputTokens,
            long generationTimeMillis,
            List<String> supportedModels,
            boolean throwError,
            Exception errorToThrow) {
        this.responsesByTenant = new HashMap<>(
                Objects.requireNonNull(responsesByTenant, "responsesByTenant cannot be null")
        );
        this.defaultResponse = Objects.requireNonNull(
                defaultResponse, "defaultResponse cannot be null"
        );
        this.model = Objects.requireNonNull(model, "model cannot be null");
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.generationTimeMillis = generationTimeMillis;
        this.supportedModels = new HashMap<>();
        supportedModels.forEach(m -> this.supportedModels.put(m, true));
        this.throwError = throwError;
        this.errorToThrow = errorToThrow;
    }

    @Override
    public Promise<ContentGenerator.GenerationResult> generateContent(
            String tenantId,
            PromptTemplate template,
            GenerationGuardrails guardrails,
            Map<String, Object> context) {
        
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(guardrails, "guardrails cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        if (throwError) {
            return Promise.ofException(
                    errorToThrow != null ? errorToThrow :
                            new RuntimeException("Mock error from MockContentGenerator")
            );
        }

        String content = responsesByTenant.getOrDefault(tenantId, defaultResponse);
        return Promise.of(new ContentGenerator.GenerationResult(
                content,
                model,
                inputTokens,
                outputTokens,
                generationTimeMillis,
                "stop",
                Collections.emptyMap()
        ));
    }

    @Override
    public Promise<List<ContentGenerator.GenerationResult>> generateBatch(
            String tenantId,
            List<PromptTemplate> templates,
            GenerationGuardrails guardrails,
            Map<String, Object> context) {
        
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(templates, "templates cannot be null");
        Objects.requireNonNull(guardrails, "guardrails cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        if (templates.isEmpty()) {
            throw new IllegalArgumentException("templates cannot be empty");
        }

        if (throwError) {
            return Promise.ofException(
                    errorToThrow != null ? errorToThrow :
                            new RuntimeException("Mock error in batch generation")
            );
        }

        String content = responsesByTenant.getOrDefault(tenantId, defaultResponse);
        List<ContentGenerator.GenerationResult> results = new ArrayList<>();
        
        for (int i = 0; i < templates.size(); i++) {
            results.add(new ContentGenerator.GenerationResult(
                    content + " [Item " + (i + 1) + "]",
                    model,
                    inputTokens,
                    outputTokens,
                    generationTimeMillis,
                    "stop",
                    Collections.singletonMap("index", i)
            ));
        }

        return Promise.of(results);
    }

    @Override
    public Promise<Void> updateConfiguration(
            String tenantId,
            Map<String, Object> configuration) {
        
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(configuration, "configuration cannot be null");

        return Promise.of((Void) null);
    }

    @Override
    public Promise<Map<String, Object>> getConfiguration(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        
        Map<String, Object> config = new HashMap<>();
        config.put("model", model);
        config.put("inputTokens", inputTokens);
        config.put("outputTokens", outputTokens);
        config.put("generationTime", generationTimeMillis);
        
        return Promise.of(config);
    }

    @Override
    public Promise<List<String>> getSupportedModels() {
        return Promise.of(new ArrayList<>(supportedModels.keySet()));
    }

    @Override
    public Promise<ContentGenerator.TemplateValidationResult> validateTemplate(
            String tenantId,
            PromptTemplate template) {
        
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(template, "template cannot be null");

        List<String> errors = new ArrayList<>();
        if (!template.isComplete()) {
            errors.add("Template has missing variables: " + template.getMissingVariables());
        }

        return Promise.of(new ContentGenerator.TemplateValidationResult(
                errors.isEmpty(),
                errors,
                template.getReferencedVariables()
        ));
    }

    /**
     * Adds response for specific tenant.
     *
     * @param tenantId tenant identifier
     * @param response response text
     */
    public void setTenantResponse(String tenantId, String response) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(response, "response cannot be null");
        responsesByTenant.put(tenantId, response);
    }

    /**
     * Gets response for specific tenant.
     *
     * @param tenantId tenant identifier
     * @return response text or null if not set
     */
    public String getTenantResponse(String tenantId) {
        return responsesByTenant.get(tenantId);
    }

    /**
     * Creates builder for MockContentGenerator.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for MockContentGenerator.
     */
    public static class Builder {
        private Map<String, String> responsesByTenant = new HashMap<>();
        private String defaultResponse = "Mock generated content";
        private String model = "mock-gpt-4";
        private int inputTokens = 100;
        private int outputTokens = 200;
        private long generationTimeMillis = 500;
        private List<String> supportedModels = Arrays.asList("mock-gpt-4", "mock-gpt-3.5");
        private boolean throwError = false;
        private Exception errorToThrow = null;

        /**
         * Sets default response.
         *
         * @param response default response text
         * @return this builder
         */
        public Builder respondWith(String response) {
            Objects.requireNonNull(response, "response cannot be null");
            this.defaultResponse = response;
            return this;
        }

        /**
         * Adds tenant-specific response.
         *
         * @param tenantId tenant identifier
         * @param response response text
         * @return this builder
         */
        public Builder respondForTenant(String tenantId, String response) {
            Objects.requireNonNull(tenantId, "tenantId cannot be null");
            Objects.requireNonNull(response, "response cannot be null");
            responsesByTenant.put(tenantId, response);
            return this;
        }

        /**
         * Sets model name.
         *
         * @param model model name
         * @return this builder
         */
        public Builder model(String model) {
            Objects.requireNonNull(model, "model cannot be null");
            this.model = model;
            return this;
        }

        /**
         * Sets token costs.
         *
         * @param inputTokens input token count
         * @param outputTokens output token count
         * @return this builder
         */
        public Builder tokenCost(int inputTokens, int outputTokens) {
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            return this;
        }

        /**
         * Sets generation time simulation.
         *
         * @param millis generation time in milliseconds
         * @return this builder
         */
        public Builder generationTime(long millis) {
            this.generationTimeMillis = millis;
            return this;
        }

        /**
         * Sets supported models.
         *
         * @param models list of model names
         * @return this builder
         */
        public Builder supportedModels(String... models) {
            Objects.requireNonNull(models, "models cannot be null");
            this.supportedModels = Arrays.asList(models);
            return this;
        }

        /**
         * Configures generator to throw error.
         *
         * @param error exception to throw
         * @return this builder
         */
        public Builder throwError(Exception error) {
            this.throwError = true;
            this.errorToThrow = Objects.requireNonNull(error, "error cannot be null");
            return this;
        }

        /**
         * Builds MockContentGenerator.
         *
         * @return configured mock generator
         */
        public MockContentGenerator build() {
            return new MockContentGenerator(
                    responsesByTenant,
                    defaultResponse,
                    model,
                    inputTokens,
                    outputTokens,
                    generationTimeMillis,
                    supportedModels,
                    throwError,
                    errorToThrow
            );
        }
    }
}
