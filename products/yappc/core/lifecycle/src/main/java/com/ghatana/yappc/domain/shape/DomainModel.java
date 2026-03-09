package com.ghatana.yappc.domain.shape;

import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Domain model with entities and relationships
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record DomainModel(
    List<EntitySpec> entities,
    List<RelationshipSpec> relationships,
    List<BoundedContextSpec> boundedContexts
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private List<EntitySpec> entities = List.of();
        private List<RelationshipSpec> relationships = List.of();
        private List<BoundedContextSpec> boundedContexts = List.of();
        
        public Builder entities(List<EntitySpec> entities) {
            this.entities = entities;
            return this;
        }
        
        public Builder relationships(List<RelationshipSpec> relationships) {
            this.relationships = relationships;
            return this;
        }
        
        public Builder boundedContexts(List<BoundedContextSpec> boundedContexts) {
            this.boundedContexts = boundedContexts;
            return this;
        }
        
        public DomainModel build() {
            return new DomainModel(entities, relationships, boundedContexts);
        }
    }
}
