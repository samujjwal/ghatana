package com.ghatana.yappc.domain.observe;

import java.time.Instant;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Log entry
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record LogEntry(
    String level,
    String message,
    Instant timestamp,
    Map<String, String> context
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String level = "INFO";
        private String message;
        private Instant timestamp = Instant.now();
        private Map<String, String> context = Map.of();
        
        public Builder level(String level) {
            this.level = level;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder context(Map<String, String> context) {
            this.context = context;
            return this;
        }
        
        public LogEntry build() {
            return new LogEntry(level, message, timestamp, context);
        }
    }
}
