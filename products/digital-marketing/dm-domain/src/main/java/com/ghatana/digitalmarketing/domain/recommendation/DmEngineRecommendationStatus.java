package com.ghatana.digitalmarketing.domain.recommendation;

/**
 * Status for engine-generated recommendations.
 *
 * @doc.type class
 * @doc.purpose Lifecycle status for DmEngineRecommendation (DMOS-F3-001)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmEngineRecommendationStatus {
    PENDING,
    ACTIVE,
    ACCEPTED,
    REJECTED,
    EXPIRED
}
