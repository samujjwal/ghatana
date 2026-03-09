package com.ghatana.ai.llm;

import java.util.List;
import java.util.Map;

/**
 * Result from LLM completion generation.
 *
 * <p>
 * <b>Purpose</b><br>
 * Captures the complete response from an LLM completion request, including text
 * output, token usage, and optional tool calls.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * CompletionResult result = service.complete(request).getResult();
 * if (result.hasToolCalls()) {
 *     for (ToolCall call : result.getToolCalls()) {
 *         // Execute tool
 *     }
 * } else {
 *     String response = result.getText();
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose LLM completion response with tool call support
 * @doc.layer infrastructure
 * @doc.pattern Value Object
 */
public final class CompletionResult {

    private final String text;
    private final int tokensUsed;
    private final int promptTokens;
    private final int completionTokens;
    private final String finishReason;
    private final String modelUsed;
    private final Map<String, Object> metadata;
    private final List<ToolCall> toolCalls;
    private final long latencyMs;

    private CompletionResult(Builder builder) {
        this.text = builder.text != null ? builder.text : "";
        this.tokensUsed = builder.tokensUsed;
        this.promptTokens = builder.promptTokens;
        this.completionTokens = builder.completionTokens;
        this.finishReason = builder.finishReason;
        this.modelUsed = builder.modelUsed;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
        this.toolCalls = builder.toolCalls != null ? List.copyOf(builder.toolCalls) : List.of();
        this.latencyMs = builder.latencyMs;
    }

    public String getText() {
        return text;
    }
    
    /**
     * Convenience method - alias for getText().
     * 
     * @return the completion text
     */
    public String text() {
        return text;
    }
    
    /**
     * Convenience method - alias for getModelUsed().
     * 
     * @return the model identifier
     */
    public String model() {
        return modelUsed;
    }

    public int getTokensUsed() {
        return tokensUsed;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    /**
     * Checks if this result contains tool calls.
     *
     * @return true if the LLM requested tool execution
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /**
     * Checks if the finish reason indicates tool use.
     *
     * @return true if finished due to tool calls
     */
    public boolean isToolUseFinish() {
        return "tool_calls".equals(finishReason) || "function_call".equals(finishReason);
    }

    /**
     * Creates a simple completion result with just text.
     *
     * @param text The completion text
     * @return A CompletionResult with the given text
     */
    public static CompletionResult of(String text) {
        return builder().text(text).finishReason("stop").build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String text;
        private int tokensUsed;
        private int promptTokens;
        private int completionTokens;
        private String finishReason;
        private String modelUsed;
        private Map<String, Object> metadata;
        private List<ToolCall> toolCalls;
        private long latencyMs;

        private Builder() {
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder tokensUsed(int tokensUsed) {
            this.tokensUsed = tokensUsed;
            return this;
        }

        public Builder promptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }

        public Builder completionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }

        public Builder finishReason(String finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public Builder modelUsed(String modelUsed) {
            this.modelUsed = modelUsed;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public Builder latencyMs(long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        public CompletionResult build() {
            return new CompletionResult(this);
        }
    }
}
