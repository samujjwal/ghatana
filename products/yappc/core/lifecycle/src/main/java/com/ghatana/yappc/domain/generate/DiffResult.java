package com.ghatana.yappc.domain.generate;

import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Diff between old and new artifacts
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record DiffResult(
    GeneratedArtifacts newArtifacts,
    GeneratedArtifacts oldArtifacts,
    List<ArtifactDiff> diffs
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private GeneratedArtifacts newArtifacts;
        private GeneratedArtifacts oldArtifacts;
        private List<ArtifactDiff> diffs = List.of();
        
        public Builder newArtifacts(GeneratedArtifacts newArtifacts) {
            this.newArtifacts = newArtifacts;
            return this;
        }
        
        public Builder oldArtifacts(GeneratedArtifacts oldArtifacts) {
            this.oldArtifacts = oldArtifacts;
            return this;
        }
        
        public Builder diffs(List<ArtifactDiff> diffs) {
            this.diffs = diffs;
            return this;
        }
        
        public DiffResult build() {
            return new DiffResult(newArtifacts, oldArtifacts, diffs);
        }
    }
}
