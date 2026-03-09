package com.ghatana.yappc.domain.generate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Collection of generated artifacts
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record GeneratedArtifacts(
    String id,
    String specRef,
    List<Artifact> artifacts,
    Instant generatedAt,
    String generatorVersion,
    Map<String, String> metadata
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String specRef;
        private List<Artifact> artifacts = List.of();
        private Instant generatedAt = Instant.now();
        private String generatorVersion = "1.0.0";
        private Map<String, String> metadata = Map.of();
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder specRef(String specRef) {
            this.specRef = specRef;
            return this;
        }
        
        public Builder artifacts(List<Artifact> artifacts) {
            this.artifacts = artifacts;
            return this;
        }
        
        public Builder generatedAt(Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }
        
        public Builder generatorVersion(String generatorVersion) {
            this.generatorVersion = generatorVersion;
            return this;
        }
        
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public GeneratedArtifacts build() {
            return new GeneratedArtifacts(id, specRef, artifacts, generatedAt, generatorVersion, metadata);
        }
    }
}
