package com.ghatana.yappc.domain.evolve;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Evolution plan for continuous improvement
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record EvolutionPlan(
    String id,
    String insightsRef,
    List<EvolutionTask> tasks,
    String newIntentRef,
    Instant createdAt,
    Map<String, String> metadata
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String insightsRef;
        private List<EvolutionTask> tasks = List.of();
        private String newIntentRef;
        private Instant createdAt = Instant.now();
        private Map<String, String> metadata = Map.of();
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder insightsRef(String insightsRef) {
            this.insightsRef = insightsRef;
            return this;
        }
        
        public Builder tasks(List<EvolutionTask> tasks) {
            this.tasks = tasks;
            return this;
        }
        
        public Builder newIntentRef(String newIntentRef) {
            this.newIntentRef = newIntentRef;
            return this;
        }
        
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public EvolutionPlan build() {
            return new EvolutionPlan(id, insightsRef, tasks, newIntentRef, createdAt, metadata);
        }
    }
}
