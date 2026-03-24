package com.ghatana.yappc.domain.learn;

import java.time.Instant;
import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Historical context for deeper analysis
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record HistoricalContext(
    List<String> pastObservationRefs,
    List<Pattern> knownPatterns,
    Instant fromTimestamp,
    Instant toTimestamp
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private List<String> pastObservationRefs = List.of();
        private List<Pattern> knownPatterns = List.of();
        private Instant fromTimestamp;
        private Instant toTimestamp = Instant.now();
        
        public Builder pastObservationRefs(List<String> pastObservationRefs) {
            this.pastObservationRefs = pastObservationRefs;
            return this;
        }
        
        public Builder knownPatterns(List<Pattern> knownPatterns) {
            this.knownPatterns = knownPatterns;
            return this;
        }
        
        public Builder fromTimestamp(Instant fromTimestamp) {
            this.fromTimestamp = fromTimestamp;
            return this;
        }
        
        public Builder toTimestamp(Instant toTimestamp) {
            this.toTimestamp = toTimestamp;
            return this;
        }
        
        public HistoricalContext build() {
            return new HistoricalContext(pastObservationRefs, knownPatterns, fromTimestamp, toTimestamp);
        }
    }
}
