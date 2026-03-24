package com.ghatana.yappc.client;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Result of an SDLC workflow step execution.
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
    private final String phase;
    private final O output;
    private final TaskStatus status;
    private final Instant startTime;
    private final Instant endTime;
    private final Map<String, Object> metadata;
    private final Throwable error;
    
    private StepResult(Builder<O> builder) {
        this.stepName = builder.stepName;
        this.phase = builder.phase;
        this.output = builder.output;
        this.status = builder.status;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.metadata = Map.copyOf(builder.metadata);
        this.error = builder.error;
    }
    
    public String getStepName() {
        return stepName;
    }
    
    public String getPhase() {
        return phase;
    }
    
    public O getOutput() {
        return output;
    }
    
    public TaskStatus getStatus() {
        return status;
    }
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public Instant getEndTime() {
        return endTime;
    }
    
    public long getDurationMs() {
        return endTime.toEpochMilli() - startTime.toEpochMilli();
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }
    
    public boolean isSuccess() {
        return status == TaskStatus.SUCCESS;
    }
    
    public static <O> Builder<O> builder() {
        return new Builder<>();
    }
    
    public static final class Builder<O> {
        private String stepName;
        private String phase;
        private O output;
        private TaskStatus status = TaskStatus.SUCCESS;
        private Instant startTime = Instant.now();
        private Instant endTime = Instant.now();
        private Map<String, Object> metadata = Map.of();
        private Throwable error;
        
        public Builder<O> stepName(String stepName) {
            this.stepName = stepName;
            return this;
        }
        
        public Builder<O> phase(String phase) {
            this.phase = phase;
            return this;
        }
        
        public Builder<O> output(O output) {
            this.output = output;
            return this;
        }
        
        public Builder<O> status(TaskStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder<O> startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }
        
        public Builder<O> endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }
        
        public Builder<O> metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public Builder<O> error(Throwable error) {
            this.error = error;
            this.status = TaskStatus.FAILED;
            return this;
        }
        
        public StepResult<O> build() {
            return new StepResult<>(this);
        }
    }
}
