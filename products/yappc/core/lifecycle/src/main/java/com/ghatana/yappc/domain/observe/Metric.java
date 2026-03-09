package com.ghatana.yappc.domain.observe;

import java.time.Instant;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Runtime metric
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record Metric(
    String name,
    double value,
    String unit,
    Map<String, String> tags,
    Instant timestamp
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String name;
        private double value;
        private String unit;
        private Map<String, String> tags = Map.of();
        private Instant timestamp = Instant.now();
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder value(double value) {
            this.value = value;
            return this;
        }
        
        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }
        
        public Builder tags(Map<String, String> tags) {
            this.tags = tags;
            return this;
        }
        
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Metric build() {
            return new Metric(name, value, unit, tags, timestamp);
        }
    }
}
