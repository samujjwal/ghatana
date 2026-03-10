package com.ghatana.yappc.agent.specialists;

import java.time.Instant;
import java.util.List;

/**
 * Output from HITL Review Specialist.
 *
 * @doc.type record
 * @doc.purpose Result of human review
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record HITLReviewOutput(
    String architectureId,
    String reviewId,
    String status,
    List<String> comments,
    List<String> approvers,
    Instant reviewedAt,
    String nextAction) {}
