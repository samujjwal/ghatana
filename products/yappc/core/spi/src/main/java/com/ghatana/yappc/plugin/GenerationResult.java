package com.ghatana.yappc.plugin;

import java.util.List;

/**
 * Result of generation operation.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles generation result operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class GenerationResult {
    
    private final String generatorId;
    private final boolean success;
    private final List<Artifact> artifacts;
    
    private GenerationResult(Builder builder) {
        this.generatorId = builder.generatorId;
        this.success = builder.success;
        this.artifacts = List.copyOf(builder.artifacts);
    }
    
    public String getGeneratorId() {
        return generatorId;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public List<Artifact> getArtifacts() {
        return artifacts;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String generatorId;
        private boolean success = true;
        private List<Artifact> artifacts = List.of();
        
        public Builder generatorId(String generatorId) {
            this.generatorId = generatorId;
            return this;
        }
        
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder artifacts(List<Artifact> artifacts) {
            this.artifacts = artifacts;
            return this;
        }
        
        public GenerationResult build() {
            return new GenerationResult(this);
        }
    }
    
    public static final class Artifact {
        private final String path;
        private final String content;
        private final String type;
        
        public Artifact(String path, String content, String type) {
            this.path = path;
            this.content = content;
            this.type = type;
        }
        
        public String getPath() {
            return path;
        }
        
        public String getContent() {
            return content;
        }
        
        public String getType() {
            return type;
        }
    }
}
