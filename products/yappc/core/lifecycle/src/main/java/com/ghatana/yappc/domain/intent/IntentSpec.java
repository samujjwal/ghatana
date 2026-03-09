package com.ghatana.yappc.domain.intent;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Validated intent specification
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record IntentSpec(
    String id,
    String productName,
    String description,
    List<GoalSpec> goals,
    List<PersonaSpec> personas,
    List<ConstraintSpec> constraints,
    Map<String, String> metadata,
    Instant createdAt,
    String tenantId
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String productName;
        private String description;
        private List<GoalSpec> goals = List.of();
        private List<PersonaSpec> personas = List.of();
        private List<ConstraintSpec> constraints = List.of();
        private Map<String, String> metadata = Map.of();
        private Instant createdAt = Instant.now();
        private String tenantId;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder productName(String productName) {
            this.productName = productName;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder goals(List<GoalSpec> goals) {
            this.goals = goals;
            return this;
        }
        
        public Builder personas(List<PersonaSpec> personas) {
            this.personas = personas;
            return this;
        }
        
        public Builder constraints(List<ConstraintSpec> constraints) {
            this.constraints = constraints;
            return this;
        }
        
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        public IntentSpec build() {
            return new IntentSpec(id, productName, description, goals, personas, 
                constraints, metadata, createdAt, tenantId);
        }
    }
}
