package com.ghatana.digitalmarketing.domain.optimization;

/**
 * Status of an experiment suggestion.
 *
 * @doc.type enum
 * @doc.purpose Defines the lifecycle states of experiment suggestions (P3-004)
 * @doc.layer product
 */
public enum ExperimentSuggestionStatus {
    /** Suggestion generated and awaiting review */
    PENDING,
    
    /** Suggestion approved and experiment created */
    APPROVED,
    
    /** Suggestion rejected with reason */
    REJECTED,
    
    /** Suggestion expired without being processed */
    EXPIRED
}
