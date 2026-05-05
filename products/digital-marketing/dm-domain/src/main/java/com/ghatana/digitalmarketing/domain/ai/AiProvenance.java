/**
 * P1-029: AI/Model Provenance Record.
 *
 * Comprehensive tracking of AI model usage for strategy and budget generation.
 * Provides full audit trail of which models were used, their configuration,
 * token usage, and confidence metrics.
 *
 * @doc.type class
 * @doc.purpose AI model provenance tracking for strategy and budget generation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
package com.ghatana.digitalmarketing.domain.ai;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable record of AI model invocation for audit and reproducibility.
 */
public final class AiProvenance {

    private final String provenanceId;
    private final String modelProvider;
    private final String modelName;
    private final String modelVersion;
    private final String promptVersion;
    private final Double temperature;
    private final Integer maxTokens;
    private final Integer inputTokens;
    private final Integer outputTokens;
    private final Double confidenceScore;
    private final String chainOfThoughtTraceId;
    private final Instant invokedAt;
    private final String invokedBy;
    private final String correlationId;
    private final Map<String, String> additionalParameters;

    private AiProvenance(Builder builder) {
        this.provenanceId = Objects.requireNonNull(builder.provenanceId, "provenanceId must not be null");
        this.modelProvider = Objects.requireNonNull(builder.modelProvider, "modelProvider must not be null");
        this.modelName = Objects.requireNonNull(builder.modelName, "modelName must not be null");
        this.modelVersion = Objects.requireNonNull(builder.modelVersion, "modelVersion must not be null");
        this.promptVersion = Objects.requireNonNull(builder.promptVersion, "promptVersion must not be null");
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.inputTokens = builder.inputTokens;
        this.outputTokens = builder.outputTokens;
        this.confidenceScore = builder.confidenceScore;
        this.chainOfThoughtTraceId = builder.chainOfThoughtTraceId;
        this.invokedAt = Objects.requireNonNull(builder.invokedAt, "invokedAt must not be null");
        this.invokedBy = Objects.requireNonNull(builder.invokedBy, "invokedBy must not be null");
        this.correlationId = builder.correlationId;
        this.additionalParameters = builder.additionalParameters == null 
            ? Map.of() 
            : Map.copyOf(builder.additionalParameters);

        if (this.provenanceId.isBlank()) {
            throw new IllegalArgumentException("provenanceId must not be blank");
        }
        if (this.modelProvider.isBlank()) {
            throw new IllegalArgumentException("modelProvider must not be blank");
        }
        if (this.modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be blank");
        }
        if (this.modelVersion.isBlank()) {
            throw new IllegalArgumentException("modelVersion must not be blank");
        }
        if (this.promptVersion.isBlank()) {
            throw new IllegalArgumentException("promptVersion must not be blank");
        }
        if (this.invokedBy.isBlank()) {
            throw new IllegalArgumentException("invokedBy must not be blank");
        }
        if (this.temperature != null && (this.temperature < 0 || this.temperature > 2)) {
            throw new IllegalArgumentException("temperature must be between 0 and 2");
        }
        if (this.confidenceScore != null && (this.confidenceScore < 0 || this.confidenceScore > 1)) {
            throw new IllegalArgumentException("confidenceScore must be between 0 and 1");
        }
    }

    public String getProvenanceId() {
        return provenanceId;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public Optional<Double> getTemperature() {
        return Optional.ofNullable(temperature);
    }

    public Optional<Integer> getMaxTokens() {
        return Optional.ofNullable(maxTokens);
    }

    public Optional<Integer> getInputTokens() {
        return Optional.ofNullable(inputTokens);
    }

    public Optional<Integer> getOutputTokens() {
        return Optional.ofNullable(outputTokens);
    }

    public Optional<Integer> getTotalTokens() {
        if (inputTokens != null && outputTokens != null) {
            return Optional.of(inputTokens + outputTokens);
        }
        return Optional.empty();
    }

    public Optional<Double> getConfidenceScore() {
        return Optional.ofNullable(confidenceScore);
    }

    public Optional<String> getChainOfThoughtTraceId() {
        return Optional.ofNullable(chainOfThoughtTraceId);
    }

    public Instant getInvokedAt() {
        return invokedAt;
    }

    public String getInvokedBy() {
        return invokedBy;
    }

    public Optional<String> getCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    public Map<String, String> getAdditionalParameters() {
        return additionalParameters;
    }

    /**
     * Returns a human-readable summary of the model invocation.
     */
    public String getSummary() {
        return String.format("%s/%s v%s (prompt v%s) at %s by %s",
            modelProvider, modelName, modelVersion, promptVersion, invokedAt, invokedBy);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String provenanceId;
        private String modelProvider;
        private String modelName;
        private String modelVersion;
        private String promptVersion;
        private Double temperature;
        private Integer maxTokens;
        private Integer inputTokens;
        private Integer outputTokens;
        private Double confidenceScore;
        private String chainOfThoughtTraceId;
        private Instant invokedAt;
        private String invokedBy;
        private String correlationId;
        private Map<String, String> additionalParameters;

        private Builder() {
        }

        public Builder provenanceId(String provenanceId) {
            this.provenanceId = provenanceId;
            return this;
        }

        public Builder modelProvider(String modelProvider) {
            this.modelProvider = modelProvider;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder modelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
            return this;
        }

        public Builder promptVersion(String promptVersion) {
            this.promptVersion = promptVersion;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder inputTokens(Integer inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        public Builder outputTokens(Integer outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        public Builder confidenceScore(Double confidenceScore) {
            this.confidenceScore = confidenceScore;
            return this;
        }

        public Builder chainOfThoughtTraceId(String chainOfThoughtTraceId) {
            this.chainOfThoughtTraceId = chainOfThoughtTraceId;
            return this;
        }

        public Builder invokedAt(Instant invokedAt) {
            this.invokedAt = invokedAt;
            return this;
        }

        public Builder invokedBy(String invokedBy) {
            this.invokedBy = invokedBy;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder additionalParameters(Map<String, String> additionalParameters) {
            this.additionalParameters = additionalParameters;
            return this;
        }

        public AiProvenance build() {
            return new AiProvenance(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AiProvenance other)) return false;
        return provenanceId.equals(other.provenanceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provenanceId);
    }

    @Override
    public String toString() {
        return "AiProvenance{" +
            "provenanceId='" + provenanceId + '\'' +
            ", modelProvider='" + modelProvider + '\'' +
            ", modelName='" + modelName + '\'' +
            ", modelVersion='" + modelVersion + '\'' +
            ", invokedAt=" + invokedAt +
            '}';
    }
}
