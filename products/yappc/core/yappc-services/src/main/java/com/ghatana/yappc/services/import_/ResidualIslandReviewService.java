/**
 * Residual Island Review Service
 * 
 * Manages review flow for residual islands.
 * Handles approval/rejection decisions for unmapped components.
 * 
 * @doc.type interface
 * @doc.purpose Residual island review
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.import_;

import java.util.List;

/**
 * Service interface for managing residual island review flow.
 */
public interface ResidualIslandReviewService {

    /**
     * Initiates a review for a residual island.
     * 
     * @param residualIsland The residual island to review
     * @param projectId The project ID
     * @param importJobId The import job ID
     * @param reviewerId The reviewer ID
     * @return Review session ID
     */
    String initiateReview(ResidualIsland residualIsland, String projectId, String importJobId, String reviewerId);

    /**
     * Submits a review decision for a residual island.
     * 
     * @param reviewSessionId The review session ID
     * @param decision The review decision
     * @param reason The reason for the decision
     * @param reviewerId The reviewer ID
     */
    void submitReviewDecision(String reviewSessionId, ReviewDecision decision, String reason, String reviewerId);

    /**
     * Gets the review status of a residual island.
     * 
     * @param reviewSessionId The review session ID
     * @return Review status
     */
    ReviewStatus getReviewStatus(String reviewSessionId);

    /**
     * Gets all pending reviews for a project.
     * 
     * @param projectId The project ID
     * @return List of pending review sessions
     */
    List<ReviewSession> getPendingReviews(String projectId);

    /**
     * Gets all reviews for an import job.
     * 
     * @param importJobId The import job ID
     * @return List of review sessions
     */
    List<ReviewSession> getReviewsByImportJob(String importJobId);
}

/**
 * Review decision.
 */
enum ReviewDecision {
    APPROVE,
    REJECT,
    REQUEST_CHANGES,
    DEFER
}

/**
 * Review status.
 */
enum ReviewStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CHANGES_REQUESTED,
    DEFERRED
}

/**
 * Review session.
 */
record ReviewSession(
    String reviewSessionId,
    String residualIslandId,
    String projectId,
    String importJobId,
    ReviewStatus status,
    ReviewDecision decision,
    String reason,
    String reviewerId,
    String reviewerName,
    java.time.Instant createdAt,
    java.time.Instant updatedAt
) {}
