package com.ghatana.yappc.domain.generate;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Diff for a single artifact with line ranges and ownership
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record ArtifactDiff(
    String artifactId,
    String changeType,
    String oldContentRef,
    String newContentRef,
    String diffText,
    List<DiffRegion> diffRegions,
    DiffOwnership ownership
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
        private List<DiffRegion> diffRegions = List.of();
        private DiffOwnership ownership;

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

        public Builder diffRegions(List<DiffRegion> diffRegions) {
            this.diffRegions = diffRegions != null ? diffRegions : List.of();
            return this;
        }

        public Builder ownership(DiffOwnership ownership) {
            this.ownership = ownership;
            return this;
        }

        public ArtifactDiff build() {
            return new ArtifactDiff(artifactId, changeType, oldContentRef, newContentRef, diffText, diffRegions, ownership);
        }
    }

    /**
     * Diff region with line ranges.
     */
    public record DiffRegion(
        int oldStartLine,
        int oldEndLine,
        int newStartLine,
        int newEndLine,
        String regionType, // "added", "removed", "modified", "context"
        String content
    ) {}

    /**
     * Ownership information for the diff.
     */
    public record DiffOwnership(
        String actorId,
        String actorType, // "ai", "user", "system"
        String sourceId, // AI model ID, user ID, or system component
        String sessionId,
        String generationRunId,
        Instant timestamp,
        Map<String, String> metadata
    ) {
        public DiffOwnership {
            timestamp = timestamp != null ? timestamp : java.time.Instant.now();
        }
    }
}
