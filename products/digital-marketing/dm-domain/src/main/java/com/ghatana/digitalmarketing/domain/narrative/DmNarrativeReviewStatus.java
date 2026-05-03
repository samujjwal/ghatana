package com.ghatana.digitalmarketing.domain.narrative;

/**
 * Status of a narrative review.
 *
 * @doc.type class
 * @doc.purpose Tracks narrative review generation state (DMOS-F3-006)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmNarrativeReviewStatus {
    PENDING,
    GENERATING,
    READY,
    FAILED
}
