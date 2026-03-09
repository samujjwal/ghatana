package com.ghatana.yappc.client;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Result of a task execution.
 *
 * @param <R> the result type
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles task result operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class TaskResult<R> {
    
    private final String taskId;
    private final R result;
    private final TaskStatus status;
    private final Instant startTime;
    private final Instant endTime;
    private final Map<String, Object> metadata;
    private final Throwable error;
    
    private TaskResult(Builder<R> builder) {
        this.taskId = builder.taskId;
        this.result = builder.result;
        this.status = builder.status;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.metadata = Map.copyOf(builder.metadata);
        this.error = builder.error;
    }
    
    public String getTaskId() {
        return taskId;
    }
    
    public R getResult() {
        return result;
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
    
    public boolean isFailure() {
        return status == TaskStatus.FAILED;
    }
    
    public static <R> Builder<R> builder() {
        return new Builder<>();
    }
    
    public static final class Builder<R> {
        private String taskId;
        private R result;
        private TaskStatus status = TaskStatus.SUCCESS;
        private Instant startTime = Instant.now();
        private Instant endTime = Instant.now();
        private Map<String, Object> metadata = Map.of();
        private Throwable error;
        
        public Builder<R> taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }
        
        public Builder<R> result(R result) {
            this.result = result;
            return this;
        }
        
        public Builder<R> status(TaskStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder<R> startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }
        
        public Builder<R> endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }
        
        public Builder<R> metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public Builder<R> error(Throwable error) {
            this.error = error;
            this.status = TaskStatus.FAILED;
            return this;
        }
        
        public TaskResult<R> build() {
            return new TaskResult<>(this);
        }
    }
}
