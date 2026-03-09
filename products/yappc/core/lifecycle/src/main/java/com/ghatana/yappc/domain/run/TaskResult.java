package com.ghatana.yappc.domain.run;

/**
 * @doc.type record
 * @doc.purpose Result of individual task execution
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record TaskResult(
    String taskId,
    RunStatus status,
    String output,
    String error,
    long durationMs
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String taskId;
        private RunStatus status = RunStatus.PENDING;
        private String output;
        private String error;
        private long durationMs = 0;
        
        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }
        
        public Builder status(RunStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder output(String output) {
            this.output = output;
            return this;
        }
        
        public Builder error(String error) {
            this.error = error;
            return this;
        }
        
        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }
        
        public TaskResult build() {
            return new TaskResult(taskId, status, output, error, durationMs);
        }
    }
}
