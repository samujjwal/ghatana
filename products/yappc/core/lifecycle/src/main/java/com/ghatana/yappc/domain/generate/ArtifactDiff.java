package com.ghatana.yappc.domain.generate;

/**
 * @doc.type record
 * @doc.purpose Diff for a single artifact
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record ArtifactDiff(
    String artifactId,
    String changeType,
    String oldContentRef,
    String newContentRef,
    String diffText
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String artifactId;
        private String changeType;
        private String oldContentRef;
        private String newContentRef;
        private String diffText;
        
        public Builder artifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }
        
        public Builder changeType(String changeType) {
            this.changeType = changeType;
            return this;
        }
        
        public Builder oldContentRef(String oldContentRef) {
            this.oldContentRef = oldContentRef;
            return this;
        }
        
        public Builder newContentRef(String newContentRef) {
            this.newContentRef = newContentRef;
            return this;
        }
        
        public Builder diffText(String diffText) {
            this.diffText = diffText;
            return this;
        }
        
        public ArtifactDiff build() {
            return new ArtifactDiff(artifactId, changeType, oldContentRef, newContentRef, diffText);
        }
    }
}
