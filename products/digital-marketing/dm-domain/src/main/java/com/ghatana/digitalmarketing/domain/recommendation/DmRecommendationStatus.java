package com.ghatana.digitalmarketing.domain.recommendation;

/**
 * Status lifecycle for an agent recommendation processed by the gateway.
 *
 * @doc.type class
 * @doc.purpose Tracks recommendation-to-command conversion state (DMOS-F2-005)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmRecommendationStatus {
    /** Recommendation received, not yet evaluated. */
    PENDING,
    /** Recommendation accepted and converted to a command. */
    ACCEPTED,
    /** Recommendation rejected by safety check or policy. */
    REJECTED,
    /** Recommendation expired before processing. */
    EXPIRED
}
