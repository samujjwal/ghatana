package com.ghatana.yappc.domain.generate;

/**
 * @doc.type record
 * @doc.purpose Individual generated artifact
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record Artifact(
    String id,
    String name,
    String type,
    String language,
    String path,
    String contentRef,
    long sizeBytes
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String name;
        private String type;
        private String language;
        private String path;
        private String contentRef;
        private long sizeBytes = 0;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder type(String type) {
            this.type = type;
            return this;
        }
        
        public Builder language(String language) {
            this.language = language;
            return this;
        }
        
        public Builder path(String path) {
            this.path = path;
            return this;
        }
        
        public Builder contentRef(String contentRef) {
            this.contentRef = contentRef;
            return this;
        }
        
        public Builder sizeBytes(long sizeBytes) {
            this.sizeBytes = sizeBytes;
            return this;
        }
        
        public Artifact build() {
            return new Artifact(id, name, type, language, path, contentRef, sizeBytes);
        }
    }
}
