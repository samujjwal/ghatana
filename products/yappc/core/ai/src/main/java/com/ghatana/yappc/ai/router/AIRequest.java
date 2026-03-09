package com.ghatana.yappc.ai.router;

import java.util.*;

/**
 * Represents an AI request with task context and parameters.
 * 
 * @doc.type class
 * @doc.purpose AI request encapsulation
 
 * @doc.layer core
 * @doc.pattern DTO
*/
public final class AIRequest {
    
    private final String requestId;
    private final TaskType taskType;
    private final String prompt;
    private final Map<String, Object> context;
    private final RequestParameters parameters;
    private final long timestamp;
    
    private AIRequest(Builder builder) {
        this.requestId = builder.requestId;
        this.taskType = builder.taskType;
        this.prompt = builder.prompt;
        this.context = Collections.unmodifiableMap(new HashMap<>(builder.context));
        this.parameters = builder.parameters;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getRequestId() { return requestId; }
    public TaskType getTaskType() { return taskType; }
    public String getPrompt() { return prompt; }
    public Map<String, Object> getContext() { return context; }
    public RequestParameters getParameters() { return parameters; }
    public long getTimestamp() { return timestamp; }
    
    /**
     * Generates semantic fingerprint for caching.
     */
    public String getSemanticFingerprint() {
        return String.format("%s:%s:%d", 
            taskType, 
            prompt.hashCode(), 
            context.hashCode());
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String requestId = UUID.randomUUID().toString();
        private TaskType taskType = TaskType.GENERAL;
        private String prompt;
        private Map<String, Object> context = new HashMap<>();
        private RequestParameters parameters = RequestParameters.defaults();
        
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }
        
        public Builder taskType(TaskType taskType) {
            this.taskType = taskType;
            return this;
        }
        
        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }
        
        public Builder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }
        
        public Builder addContext(String key, Object value) {
            this.context.put(key, value);
            return this;
        }
        
        public Builder parameters(RequestParameters parameters) {
            this.parameters = parameters;
            return this;
        }
        
        public AIRequest build() {
            Objects.requireNonNull(prompt, "prompt is required");
            return new AIRequest(this);
        }
    }
    
    /**
     * Task types for intelligent model selection.
     */
    public enum TaskType {
        /** General chat and conversation */
        CHAT,
        
        /** Code generation and modification */
        CODE_GENERATION,
        
        /** Code analysis and review */
        CODE_ANALYSIS,
        
        /** Complex reasoning tasks */
        REASONING,
        
        /** Fast, simple responses */
        FAST_RESPONSE,
        
        /** Document generation */
        DOCUMENTATION,
        
        /** Test generation */
        TEST_GENERATION,
        
        /** General purpose */
        GENERAL
    }
    
    /**
     * Request parameters for model execution.
     */
    public static final class RequestParameters {
        private final double temperature;
        private final int maxTokens;
        private final double topP;
        private final int topK;
        private final List<String> stopSequences;
        
        private RequestParameters(Builder builder) {
            this.temperature = builder.temperature;
            this.maxTokens = builder.maxTokens;
            this.topP = builder.topP;
            this.topK = builder.topK;
            this.stopSequences = List.copyOf(builder.stopSequences);
        }
        
        public double getTemperature() { return temperature; }
        public int getMaxTokens() { return maxTokens; }
        public double getTopP() { return topP; }
        public int getTopK() { return topK; }
        public List<String> getStopSequences() { return stopSequences; }
        
        public static RequestParameters defaults() {
            return builder().build();
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private double temperature = 0.7;
            private int maxTokens = 2048;
            private double topP = 0.9;
            private int topK = 40;
            private List<String> stopSequences = new ArrayList<>();
            
            public Builder temperature(double temperature) {
                this.temperature = temperature;
                return this;
            }
            
            public Builder maxTokens(int maxTokens) {
                this.maxTokens = maxTokens;
                return this;
            }
            
            public Builder topP(double topP) {
                this.topP = topP;
                return this;
            }
            
            public Builder topK(int topK) {
                this.topK = topK;
                return this;
            }
            
            public Builder stopSequences(List<String> stopSequences) {
                this.stopSequences = stopSequences;
                return this;
            }
            
            public RequestParameters build() {
                return new RequestParameters(this);
            }
        }
    }
}
