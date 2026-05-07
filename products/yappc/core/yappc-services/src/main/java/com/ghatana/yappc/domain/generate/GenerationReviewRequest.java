package com.ghatana.yappc.domain.generate;

/**
 * @doc.type record
 * @doc.purpose Backend service request for a generated artifact review decision
 * @doc.layer domain
 */
public record GenerationReviewRequest(
    String runId,
    String projectId,
    String actorId,
    String reason,
    GenerationReviewAction action
) {
}
