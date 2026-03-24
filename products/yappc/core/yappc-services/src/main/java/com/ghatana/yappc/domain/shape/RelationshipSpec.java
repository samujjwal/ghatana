package com.ghatana.yappc.domain.shape;

/**
 * @doc.type record
 * @doc.purpose Entity relationship specification
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record RelationshipSpec(
    String fromEntity,
    String toEntity,
    String type,
    String description
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String fromEntity;
        private String toEntity;
        private String type;
        private String description;
        
        public Builder fromEntity(String fromEntity) {
            this.fromEntity = fromEntity;
            return this;
        }
        
        public Builder toEntity(String toEntity) {
            this.toEntity = toEntity;
            return this;
        }
        
        public Builder type(String type) {
            this.type = type;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public RelationshipSpec build() {
            return new RelationshipSpec(fromEntity, toEntity, type, description);
        }
    }
}
