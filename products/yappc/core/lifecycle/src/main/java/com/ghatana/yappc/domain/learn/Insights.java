package com.ghatana.yappc.domain.learn;

import java.time.Instant;
import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Insights from learning phase
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record Insights(
    String id,
    String observationRef,
    List<Pattern> patterns,
    List<Anomaly> anomalies,
    List<Recommendation> recommendations,
    Instant generatedAt
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String observationRef;
        private List<Pattern> patterns = List.of();
        private List<Anomaly> anomalies = List.of();
        private List<Recommendation> recommendations = List.of();
        private Instant generatedAt = Instant.now();
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder observationRef(String observationRef) {
            this.observationRef = observationRef;
            return this;
        }
        
        public Builder patterns(List<Pattern> patterns) {
            this.patterns = patterns;
            return this;
        }
        
        public Builder anomalies(List<Anomaly> anomalies) {
            this.anomalies = anomalies;
            return this;
        }
        
        public Builder recommendations(List<Recommendation> recommendations) {
            this.recommendations = recommendations;
            return this;
        }
        
        public Builder generatedAt(Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }
        
        public Insights build() {
            return new Insights(id, observationRef, patterns, anomalies, recommendations, generatedAt);
        }
    }
}
