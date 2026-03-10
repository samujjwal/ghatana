package com.ghatana.yappc.ai.canvas.llm;

import java.util.Objects;

/**
 * Request object for LLM operations.
 *
 * @doc.type class
 * @doc.purpose LLM request value object
 * @doc.layer product
 * @doc.pattern Builder
 */
public final class LLMRequest {

    private final String prompt;
    private final int maxTokens;
    private final double temperature;
    private final String model;

    private LLMRequest(Builder builder) {
        this.prompt = Objects.requireNonNull(builder.prompt, "prompt must not be null");
        this.maxTokens = builder.maxTokens;
        this.temperature = builder.temperature;
        this.model = builder.model;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getPrompt() { return prompt; }
    public int getMaxTokens() { return maxTokens; }
    public double getTemperature() { return temperature; }
    public String getModel() { return model; }

    public static final class Builder {
        private String prompt;
        private int maxTokens = 1000;
        private double temperature = 0.7;
        private String model;

        private Builder() {}

        public Builder prompt(String prompt) { this.prompt = prompt; return this; }
        public Builder maxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
        public Builder temperature(double temperature) { this.temperature = temperature; return this; }
        public Builder model(String model) { this.model = model; return this; }

        public LLMRequest build() {
            return new LLMRequest(this);
        }
    }
}
