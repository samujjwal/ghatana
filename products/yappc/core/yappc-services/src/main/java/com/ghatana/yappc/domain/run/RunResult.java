package com.ghatana.yappc.domain.run;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Result of run execution
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record RunResult(
    String id,
    String runSpecRef,
    RunStatus status,
    List<TaskResult> taskResults,
    Instant startedAt,
    Instant completedAt,
    Map<String, String> metadata
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String runSpecRef;
        private RunStatus status = RunStatus.PENDING;
        private List<TaskResult> taskResults = List.of();
        private Instant startedAt = Instant.now();
        private Instant completedAt;
        private Map<String, String> metadata = Map.of();
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder runSpecRef(String runSpecRef) {
            this.runSpecRef = runSpecRef;
            return this;
        }
        
        public Builder status(RunStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder taskResults(List<TaskResult> taskResults) {
            this.taskResults = taskResults;
            return this;
        }
        
        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }
        
        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }
        
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public RunResult build() {
            return new RunResult(id, runSpecRef, status, taskResults, startedAt, completedAt, metadata);
        }
    }
}
