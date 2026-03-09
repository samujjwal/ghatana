package com.ghatana.yappc.domain.observe;

import java.time.Instant;
import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Observation data from runtime
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record Observation(
    String id,
    String runRef,
    List<Metric> metrics,
    List<LogEntry> logs,
    List<TraceSpan> traces,
    Instant collectedAt
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String runRef;
        private List<Metric> metrics = List.of();
        private List<LogEntry> logs = List.of();
        private List<TraceSpan> traces = List.of();
        private Instant collectedAt = Instant.now();
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder runRef(String runRef) {
            this.runRef = runRef;
            return this;
        }
        
        public Builder metrics(List<Metric> metrics) {
            this.metrics = metrics;
            return this;
        }
        
        public Builder logs(List<LogEntry> logs) {
            this.logs = logs;
            return this;
        }
        
        public Builder traces(List<TraceSpan> traces) {
            this.traces = traces;
            return this;
        }
        
        public Builder collectedAt(Instant collectedAt) {
            this.collectedAt = collectedAt;
            return this;
        }
        
        public Observation build() {
            return new Observation(id, runRef, metrics, logs, traces, collectedAt);
        }
    }
}
