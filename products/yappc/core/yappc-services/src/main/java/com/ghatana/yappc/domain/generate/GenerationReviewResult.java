package com.ghatana.yappc.domain.generate;

import java.time.Instant;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Auditable result returned after a generated artifact review decision
 * @doc.layer domain
 * @doc.pattern DTO
 */
public record GenerationReviewResult(
    String runId,
    String projectId,
    String decision,
    String status,
    boolean reviewRequired,
    String actorId,
    Instant decidedAt,
    String auditEvent,
    String message,
    Map<String, String> metadata
) {
}
