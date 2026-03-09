package com.ghatana.softwareorg.engineering.domain;

/**
 * Represents a code review in the engineering department.
 *
 * @doc.type class
 * @doc.purpose Code review domain model
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class CodeReview {

    private final String prId;
    private final String reviewer;
    private final boolean approved;
    private final String feedback;

    public CodeReview(String prId, String reviewer, boolean approved, String feedback) {
        this.prId = prId;
        this.reviewer = reviewer;
        this.approved = approved;
        this.feedback = feedback;
    }

    public String getPrId() {
        return prId;
    }

    public String getId() {
        return prId;
    }

    public String getReviewer() {
        return reviewer;
    }

    public boolean isApproved() {
        return approved;
    }

    public String getFeedback() {
        return feedback;
    }
}
