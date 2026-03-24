package com.ghatana.yappc.domain.shape;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Complete system model
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record SystemModel(
    ShapeSpec shape,
    Map<String, Object> designRationale,
    Map<String, String> diagrams
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private ShapeSpec shape;
        private Map<String, Object> designRationale = Map.of();
        private Map<String, String> diagrams = Map.of();
        
        public Builder shape(ShapeSpec shape) {
            this.shape = shape;
            return this;
        }
        
        public Builder designRationale(Map<String, Object> designRationale) {
            this.designRationale = designRationale;
            return this;
        }
        
        public Builder diagrams(Map<String, String> diagrams) {
            this.diagrams = diagrams;
            return this;
        }
        
        public SystemModel build() {
            return new SystemModel(shape, designRationale, diagrams);
        }
    }
}
