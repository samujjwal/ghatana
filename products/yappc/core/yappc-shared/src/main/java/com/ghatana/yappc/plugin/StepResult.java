package com.ghatana.yappc.plugin;

import java.time.Instant;
import java.util.Map;

/**
 * Result of SDLC step execution.
 *
 * @param <O> the output type
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles step result operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class StepResult<O> {
    
    private final String stepName;
    private final O output;
    private final boolean success;
    private final Instant timestamp;
    private final Map<String, Object> metadata;
    
    private StepResult(Builder<O> builder) {
        this.stepName = builder.stepName;
        this.output = builder.output;
        this.success = builder.success;
        this.timestamp = builder.timestamp;
        this.metadata = Map.copyOf(builder.metadata);
    }
    
    public String getStepName() {
        return stepName;
    }
    
    public O getOutput() {
        return output;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public static <O> Builder<O> builder() {
        return new Builder<>();
    }
    
    public static final class Builder<O> {
        private String stepName;
        private O output;
        private boolean success = true;
        private Instant timestamp = Instant.now();
        private Map<String, Object> metadata = Map.of();
        
        public Builder<O> stepName(String stepName) {
            this.stepName = stepName;
            return this;
        }
        
        public Builder<O> output(O output) {
            this.output = output;
            return this;
        }
        
        public Builder<O> success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder<O> timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder<O> metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public StepResult<O> build() {
            return new StepResult<>(this);
        }
    }
}
