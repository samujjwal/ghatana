/**
 * Residual Island Review Service Implementation
 * 
 * Production-grade implementation of residual island review service.
 * Manages review flow for residual islands.
 * 
 * @doc.type class
 * @doc.purpose Residual island review implementation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.import_;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade implementation of residual island review service.
 * Uses in-memory storage for demonstration; should be replaced with database persistence.
 */
public final class ResidualIslandReviewServiceImpl implements ResidualIslandReviewService {

    private static final Logger log = LoggerFactory.getLogger(ResidualIslandReviewServiceImpl.class);

    // In-memory storage for demonstration - replace with database persistence
    private final Map<String, ReviewSession> reviewSessions = new ConcurrentHashMap<>();
    private final Map<String, List<String>> projectToReviewsMap = new ConcurrentHashMap<>();
    private final Map<String, List<String>> importJobToReviewsMap = new ConcurrentHashMap<>();

    @Override
    public String initiateReview(ResidualIsland residualIsland, String projectId, String importJobId, String reviewerId) {
        String reviewSessionId = "review-" + java.util.UUID.randomUUID().toString();

        log.info("Initiating review: reviewSessionId={}, componentId={}, projectId={}", 
                reviewSessionId, residualIsland.componentId(), projectId);

        ReviewSession session = new ReviewSession(
                reviewSessionId,
                residualIsland.componentId(),
                projectId,
                importJobId,
                ReviewStatus.PENDING,
                null,
                null,
                reviewerId,
                "System",
                Instant.now(),
                Instant.now()
        );

        reviewSessions.put(reviewSessionId, session);
        projectToReviewsMap.computeIfAbsent(projectId, k -> new ArrayList<>()).add(reviewSessionId);
        importJobToReviewsMap.computeIfAbsent(importJobId, k -> new ArrayList<>()).add(reviewSessionId);

        log.info("Review initiated successfully: reviewSessionId={}", reviewSessionId);
        return reviewSessionId;
    }

    @Override
    public void submitReviewDecision(String reviewSessionId, ReviewDecision decision, String reason, String reviewerId) {
        log.info("Submitting review decision: reviewSessionId={}, decision={}, reviewerId={}", 
                reviewSessionId, decision, reviewerId);

        ReviewSession session = reviewSessions.get(reviewSessionId);

        if (session == null) {
            log.warn("Review session not found: reviewSessionId={}", reviewSessionId);
            throw new IllegalArgumentException("Review session not found: " + reviewSessionId);
        }

        ReviewStatus status = switch (decision) {
            case APPROVE -> ReviewStatus.APPROVED;
            case REJECT -> ReviewStatus.REJECTED;
            case REQUEST_CHANGES -> ReviewStatus.CHANGES_REQUESTED;
            case DEFER -> ReviewStatus.DEFERRED;
        };

        ReviewSession updatedSession = new ReviewSession(
                session.reviewSessionId(),
                session.residualIslandId(),
                session.projectId(),
                session.importJobId(),
                status,
                decision,
                reason,
                reviewerId,
                reviewerId,
                session.createdAt(),
                Instant.now()
        );

        reviewSessions.put(reviewSessionId, updatedSession);

        log.info("Review decision submitted successfully: reviewSessionId={}, status={}", 
                reviewSessionId, status);
    }

    @Override
    public ReviewStatus getReviewStatus(String reviewSessionId) {
        ReviewSession session = reviewSessions.get(reviewSessionId);

        if (session == null) {
            log.warn("Review session not found: reviewSessionId={}", reviewSessionId);
            return ReviewStatus.PENDING;
        }

        return session.status();
    }

    @Override
    public List<ReviewSession> getPendingReviews(String projectId) {
        log.debug("Getting pending reviews: projectId={}", projectId);

        List<String> reviewIds = projectToReviewsMap.getOrDefault(projectId, List.of());
        List<ReviewSession> pendingReviews = new ArrayList<>();

        for (String reviewId : reviewIds) {
            ReviewSession session = reviewSessions.get(reviewId);
            if (session != null && session.status() == ReviewStatus.PENDING) {
                pendingReviews.add(session);
            }
        }

        log.debug("Pending reviews retrieved: count={}", pendingReviews.size());
        return pendingReviews;
    }

    @Override
    public List<ReviewSession> getReviewsByImportJob(String importJobId) {
        log.debug("Getting reviews by import job: importJobId={}", importJobId);

        List<String> reviewIds = importJobToReviewsMap.getOrDefault(importJobId, List.of());
        List<ReviewSession> reviews = new ArrayList<>();

        for (String reviewId : reviewIds) {
            ReviewSession session = reviewSessions.get(reviewId);
            if (session != null) {
                reviews.add(session);
            }
        }

        log.debug("Reviews retrieved: count={}", reviews.size());
        return reviews;
    }

    /**
     * Gets a review session by ID.
     * 
     * @param reviewSessionId The review session ID
     * @return The review session, or null if not found
     */
    public ReviewSession getReviewSession(String reviewSessionId) {
        return reviewSessions.get(reviewSessionId);
    }

    /**
     * Deletes a review session.
     * 
     * @param reviewSessionId The review session ID
     */
    public void deleteReviewSession(String reviewSessionId) {
        log.info("Deleting review session: reviewSessionId={}", reviewSessionId);

        ReviewSession session = reviewSessions.remove(reviewSessionId);

        if (session != null) {
            List<String> projectReviews = projectToReviewsMap.get(session.projectId());
            if (projectReviews != null) {
                projectReviews.remove(reviewSessionId);
            }

            List<String> importJobReviews = importJobToReviewsMap.get(session.importJobId());
            if (importJobReviews != null) {
                importJobReviews.remove(reviewSessionId);
            }

            log.info("Review session deleted successfully: reviewSessionId={}", reviewSessionId);
        }
    }
}
