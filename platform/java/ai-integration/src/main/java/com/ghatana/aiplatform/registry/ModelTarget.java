package com.ghatana.aiplatform.registry;

import java.util.Objects;

/**
 * Model deployment target with configuration.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents a specific model deployment configuration including:
 * <ul>
 * <li>Model name and version</li>
 * <li>Prompt template for LLM inference</li>
 * <li>Generation parameters (max tokens, temperature)</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ModelTarget target = new ModelTarget(
 *     "gpt-4",
 *     "v1",
 *     "Is this transaction fraudulent? {context}",
 *     100,
 *     0.7
 * );
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Immutable - thread-safe.
 *
 * @doc.type class
 * @doc.purpose Model deployment configuration
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public class ModelTarget {

    private final String modelName;
    private final String modelVersion;
    private final String promptTemplate;
    private final int maxTokens;
    private final double temperature;

    /**
     * Constructs a model target.
     *
     * @param modelName model name (e.g., "gpt-4")
     * @param modelVersion model version (e.g., "v1")
     * @param promptTemplate prompt template with placeholders
     * @param maxTokens maximum tokens for generation
     * @param temperature sampling temperature (0.0-1.0)
     */
    public ModelTarget(String modelName, String modelVersion, String promptTemplate,
            int maxTokens, double temperature) {
        this.modelName = Objects.requireNonNull(modelName, "modelName must not be null");
        this.modelVersion = Objects.requireNonNull(modelVersion, "modelVersion must not be null");
        this.promptTemplate = Objects.requireNonNull(promptTemplate, "promptTemplate must not be null");
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getPromptTemplate() {
        return promptTemplate;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    @Override
    public String toString() {
        return "ModelTarget{"
                + "modelName='" + modelName + '\''
                + ", modelVersion='" + modelVersion + '\''
                + ", maxTokens=" + maxTokens
                + ", temperature=" + temperature
                + '}';
    }
}
