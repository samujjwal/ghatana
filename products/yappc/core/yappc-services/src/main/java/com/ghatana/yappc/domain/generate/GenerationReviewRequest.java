package com.ghatana.yappc.domain.generate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Backend service request for a generated artifact review decision with user edits and provenance
 * @doc.layer domain
 * @doc.pattern DTO
 */
public record GenerationReviewRequest(
    String runId,
    String projectId,
    String actorId,
    String reason,
    GenerationReviewAction action,
    List<UserEdit> userEdits,
    ReviewProvenance provenance
) {
    /**
     * Represents a user edit made during the review process.
     */
    public record UserEdit(
        String artifactId,
        String regionId,
        int startLine,
        int endLine,
        String originalContent,
        String editedContent,
        String editType, // "insert", "delete", "modify"
        Instant editTimestamp
    ) {
        public UserEdit {
            editTimestamp = editTimestamp != null ? editTimestamp : Instant.now();
        }
    }

    /**
     * Provenance information for the review decision.
     */
    public record ReviewProvenance(
        String sessionId,
        String traceId,
        String source, // "web", "api", "cli"
        String clientVersion,
        Instant decisionTimestamp,
        Map<String, String> metadata
    ) {
        public ReviewProvenance {
            decisionTimestamp = decisionTimestamp != null ? decisionTimestamp : Instant.now();
        }
    }

    /**
     * Creates a GenerationReviewRequest with minimal fields (backward compatibility).
     */
    public static GenerationReviewRequest of(
        String runId,
        String projectId,
        String actorId,
        String reason,
        GenerationReviewAction action
    ) {
        return new GenerationReviewRequest(
            runId,
            projectId,
            actorId,
            reason,
            action,
            List.of(),
            new ReviewProvenance(null, null, "api", null, Instant.now(), Map.of())
        );
    }
}
