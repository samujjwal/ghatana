package com.ghatana.digitalmarketing.domain.optimization;

/**
 * Status of a budget reallocation proposal.
 *
 * @doc.type enum
 * @doc.purpose Defines the lifecycle states of budget reallocation proposals (P3-004)
 * @doc.layer product
 */
public enum BudgetReallocationStatus {
    /** Proposal generated and awaiting human review */
    PENDING,
    
    /** Proposal approved by human, awaiting execution */
    APPROVED,
    
    /** Proposal rejected with reason */
    REJECTED,
    
    /** Proposal executed and budget reallocated */
    EXECUTED,
    
    /** Proposal expired without being processed */
    EXPIRED
}
