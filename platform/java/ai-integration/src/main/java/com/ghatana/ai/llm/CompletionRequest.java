package com.ghatana.ai.llm;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Request for LLM completion operations. Minimal value object used across AI
 * modules.
 *
 * @doc.type class
 * @doc.purpose LLM completion request
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public final class CompletionRequest {

    private final String prompt;
    private final List<ChatMessage> messages;
    private final int maxTokens;
    private final double temperature;
    private final double topP;
    private final List<String> stop;
    private final String model;
    private final Map<String, Object> metadata;
    private final List<String> stopSequences;
    private final String responseFormat;
    private final double frequencyPenalty;
    private final double presencePenalty;

    private CompletionRequest(Builder b) {
        if (b.prompt == null && (b.messages == null || b.messages.isEmpty())) {
            throw new IllegalArgumentException("Either prompt or messages must be provided");
        }
        this.prompt = b.prompt;
        this.messages = b.messages != null ? List.copyOf(b.messages) : Collections.emptyList();
        this.maxTokens = b.maxTokens;
        this.temperature = b.temperature;
        this.topP = b.topP;
        this.stop = b.stop;
        this.model = b.model;
        this.metadata = b.metadata != null ? Map.copyOf(b.metadata) : Map.of();
        this.stopSequences = b.stopSequences;
        this.responseFormat = b.responseFormat;
        this.frequencyPenalty = b.frequencyPenalty;
        this.presencePenalty = b.presencePenalty;
    }

    public String getPrompt() {
        return prompt;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getTopP() {
        return topP;
    }

    public List<String> getStop() {
        return stop;
    }

    public String getModel() {
        return model;
    }

    public List<String> getStopSequences() {
        return stopSequences;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getResponseFormat() {
        return responseFormat;
    }

    public double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public double getPresencePenalty() {
        return presencePenalty;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String prompt;
        private List<ChatMessage> messages;
        private int maxTokens = 256;
        private double temperature = 1.0;
        private double topP = 1.0;
        private List<String> stop = null;
        private String model = null;
        private Map<String, Object> metadata = null;
        private List<String> stopSequences = null;
        private String responseFormat = null;
        private double frequencyPenalty = 0.0;
        private double presencePenalty = 0.0;

        private Builder() {
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * Sets sequences that will stop text generation when encountered.
         *
         * <p>Useful for preventing overgeneration or enforcing format boundaries.
         *
         * @param stopSequences list of stop sequences
         * @return this builder
         */
        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public Builder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder frequencyPenalty(double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder presencePenalty(double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public CompletionRequest build() {
            return new CompletionRequest(this);
        }
    }
}
