package com.ghatana.yappc.client;

import java.util.List;

/**
 * Result of code generation.
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
    
    private final boolean success;
    private final List<GeneratedArtifact> artifacts;
    
    public GenerationResult(boolean success, List<GeneratedArtifact> artifacts) {
        this.success = success;
        this.artifacts = List.copyOf(artifacts);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public List<GeneratedArtifact> getArtifacts() {
        return artifacts;
    }
    
    public static final class GeneratedArtifact {
        private final String path;
        private final String content;
        private final String type;
        
        public GeneratedArtifact(String path, String content, String type) {
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
