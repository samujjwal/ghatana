package com.ghatana.virtualorg.framework.hitl;

/**
 * Status of an approval request.
 *
 * @doc.type enum
 * @doc.purpose Approval request status
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum ApprovalStatus {
    /**
     * Request is waiting for human response.
     */
    PENDING("Awaiting approval"),
    /**
     * Request has been approved by a human.
     */
    APPROVED("Approved"),
    /**
     * Request has been rejected by a human.
     */
    REJECTED("Rejected"),
    /**
     * Request has expired without a response.
     */
    EXPIRED("Expired"),
    /**
     * Request was cancelled by the requesting agent.
     */
    CANCELLED("Cancelled");

    private final String description;

    ApprovalStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Checks if this is a terminal status.
     */
    public boolean isTerminal() {
        return this != PENDING;
    }

    /**
     * Checks if this is a positive outcome.
     */
    public boolean isApproved() {
        return this == APPROVED;
    }
}
