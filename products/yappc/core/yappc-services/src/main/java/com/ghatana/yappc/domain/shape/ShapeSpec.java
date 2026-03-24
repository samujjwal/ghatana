package com.ghatana.yappc.domain.shape;

import com.ghatana.yappc.domain.intent.IntentSpec;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose System shape/design specification
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record ShapeSpec(
    String id,
    String intentRef,
    DomainModel domainModel,
    List<WorkflowSpec> workflows,
    List<IntegrationSpec> integrations,
    ArchitecturePattern architecture,
    Map<String, String> metadata,
    Instant createdAt,
    String tenantId
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String intentRef;
        private DomainModel domainModel;
        private List<WorkflowSpec> workflows = List.of();
        private List<IntegrationSpec> integrations = List.of();
        private ArchitecturePattern architecture;
        private Map<String, String> metadata = Map.of();
        private Instant createdAt = Instant.now();
        private String tenantId;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder intentRef(String intentRef) {
            this.intentRef = intentRef;
            return this;
        }
        
        public Builder domainModel(DomainModel domainModel) {
            this.domainModel = domainModel;
            return this;
        }
        
        public Builder workflows(List<WorkflowSpec> workflows) {
            this.workflows = workflows;
            return this;
        }
        
        public Builder integrations(List<IntegrationSpec> integrations) {
            this.integrations = integrations;
            return this;
        }
        
        public Builder architecture(ArchitecturePattern architecture) {
            this.architecture = architecture;
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
        
        public ShapeSpec build() {
            return new ShapeSpec(id, intentRef, domainModel, workflows, integrations, 
                architecture, metadata, createdAt, tenantId);
        }
    }
}
