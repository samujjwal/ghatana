package com.ghatana.digitalmarketing.domain.optimization;

/**
 * Status of a next-best-action recommendation.
 *
 * @doc.type enum
 * @doc.purpose Defines the lifecycle states of next-best-action recommendations (P3-004)
 * @doc.layer product
 */
public enum NextBestActionStatus {
    /** Recommendation generated and awaiting human review */
    PENDING,
    
    /** Recommendation approved by human and executed */
    APPROVED,
    
    /** Recommendation rejected by human with reason */
    REJECTED,
    
    /** Recommendation expired without being processed */
    EXPIRED,
    
    /** Recommendation execution failed */
    FAILED
}
