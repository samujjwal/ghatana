package com.ghatana.yappc.domain.observe;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Distributed trace span
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record TraceSpan(
    String traceId,
    String spanId,
    String parentSpanId,
    String operationName,
    long startTimeMs,
    long durationMs,
    Map<String, String> tags
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String traceId;
        private String spanId;
        private String parentSpanId;
        private String operationName;
        private long startTimeMs;
        private long durationMs;
        private Map<String, String> tags = Map.of();
        
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }
        
        public Builder spanId(String spanId) {
            this.spanId = spanId;
            return this;
        }
        
        public Builder parentSpanId(String parentSpanId) {
            this.parentSpanId = parentSpanId;
            return this;
        }
        
        public Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }
        
        public Builder startTimeMs(long startTimeMs) {
            this.startTimeMs = startTimeMs;
            return this;
        }
        
        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }
        
        public Builder tags(Map<String, String> tags) {
            this.tags = tags;
            return this;
        }
        
        public TraceSpan build() {
            return new TraceSpan(traceId, spanId, parentSpanId, operationName, startTimeMs, durationMs, tags);
        }
    }
}
