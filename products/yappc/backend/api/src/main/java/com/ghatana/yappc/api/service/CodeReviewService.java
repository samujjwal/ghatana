/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.platform.audit.AuditService;
import com.ghatana.yappc.api.domain.CodeReview;
import com.ghatana.yappc.api.domain.CodeReview.*;
import com.ghatana.yappc.api.repository.CodeReviewRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;

/**
 * Service for managing code reviews.
 *
 * @doc.type class
 * @doc.purpose Business logic for code review operations
 * @doc.layer service
 * @doc.pattern Service
 */
public class CodeReviewService {

    private static final Logger logger = LoggerFactory.getLogger(CodeReviewService.class);

    private final CodeReviewRepository repository;
    private final AuditService auditService;

    @Inject
    public CodeReviewService(CodeReviewRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /**
     * Creates a new code review.
     */
    public Promise<CodeReview> createReview(String tenantId, CreateReviewInput input) {
        logger.info("Creating code review for project: {}", input.projectId());

        CodeReview review = new CodeReview();
        review.setTenantId(tenantId);
        review.setProjectId(input.projectId());
        review.setStoryId(input.storyId());
        review.setPullRequestUrl(input.pullRequestUrl());
        review.setPullRequestNumber(input.pullRequestNumber());
        review.setTitle(input.title());
        review.setDescription(input.description());
        review.setAuthorId(input.authorId());
        review.setStatus(input.isDraft() ? ReviewStatus.DRAFT : ReviewStatus.OPEN);

        // Add reviewers
        if (input.reviewerIds() != null) {
            for (String reviewerId : input.reviewerIds()) {
                Reviewer reviewer = new Reviewer();
                reviewer.setUserId(reviewerId);
                reviewer.setRequired(true);
                review.addReviewer(reviewer);
            }
        }

        // Set file changes
        if (input.fileChanges() != null) {
            review.setFileChanges(input.fileChanges());
            ReviewMetrics metrics = review.getMetrics();
            metrics.setTotalFiles(input.fileChanges().size());
            int additions = input.fileChanges().stream().mapToInt(FileChange::getAdditions).sum();
            int deletions = input.fileChanges().stream().mapToInt(FileChange::getDeletions).sum();
            metrics.setAdditions(additions);
            metrics.setDeletions(deletions);
        }

        return repository.save(review);
    }

    /**
     * Gets a code review by ID.
     */
    public Promise<Optional<CodeReview>> getReview(String tenantId, UUID reviewId) {
        return repository.findById(tenantId, reviewId);
    }

    /**
     * Lists reviews for a project.
     */
    public Promise<List<CodeReview>> listProjectReviews(String tenantId, String projectId) {
        return repository.findByProject(tenantId, projectId);
    }

    /**
     * Lists reviews for a story.
     */
    public Promise<List<CodeReview>> listStoryReviews(String tenantId, String storyId) {
        return repository.findByStory(tenantId, storyId);
    }

    /**
     * Lists reviews pending for a user.
     */
    public Promise<List<CodeReview>> listPendingReviews(String tenantId, String userId) {
        return repository.findPendingForUser(tenantId, userId);
    }

    /**
     * Lists reviews authored by a user.
     */
    public Promise<List<CodeReview>> listAuthoredReviews(String tenantId, String userId) {
        return repository.findByAuthor(tenantId, userId);
    }

    /**
     * Submits a review.
     */
    public Promise<CodeReview> submitReview(String tenantId, UUID reviewId, SubmitReviewInput input) {
        return repository.findById(tenantId, reviewId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Review not found"));
                    }
                    CodeReview review = opt.get();

                    review.submitReview(input.reviewerId(), input.decision());

                    // Add comment if provided
                    if (input.comment() != null && !input.comment().isEmpty()) {
                        ReviewComment comment = new ReviewComment();
                        comment.setAuthorId(input.reviewerId());
                        comment.setType(CommentType.GENERAL);
                        comment.setContent(input.comment());
                        review.addComment(comment);
                    }

                    return repository.save(review);
                });
    }

    /**
     * Adds a comment to a review.
     */
    public Promise<CodeReview> addComment(String tenantId, UUID reviewId, AddCommentInput input) {
        return repository.findById(tenantId, reviewId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Review not found"));
                    }
                    CodeReview review = opt.get();

                    ReviewComment comment = new ReviewComment();
                    comment.setAuthorId(input.authorId());
                    comment.setAuthorName(input.authorName());
                    comment.setType(input.type());
                    comment.setContent(input.content());
                    comment.setFilePath(input.filePath());
                    comment.setLineNumber(input.lineNumber());
                    comment.setSuggestionCode(input.suggestionCode());

                    review.addComment(comment);
                    return repository.save(review);
                });
    }

    /**
     * Resolves a comment.
     */
    public Promise<CodeReview> resolveComment(String tenantId, UUID reviewId, String commentId, String resolvedBy) {
        return repository.findById(tenantId, reviewId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Review not found"));
                    }
                    CodeReview review = opt.get();

                    review.getComments().stream()
                            .filter(c -> c.getId().equals(commentId))
                            .findFirst()
                            .ifPresent(c -> {
                                c.setResolved(true);
                                c.setResolvedBy(resolvedBy);
                                c.setUpdatedAt(Instant.now());
                            });

                    // Update metrics
                    long resolved = review.getComments().stream().filter(ReviewComment::isResolved).count();
                    review.getMetrics().setResolvedComments((int) resolved);

                    return repository.save(review);
                });
    }

    /**
     * Requests changes on a review.
     */
    public Promise<CodeReview> requestChanges(String tenantId, UUID reviewId, String reviewerId, String comment) {
        SubmitReviewInput input = new SubmitReviewInput(reviewerId, ReviewDecision.REQUEST_CHANGES, comment);
        return submitReview(tenantId, reviewId, input);
    }

    /**
     * Approves a review.
     */
    public Promise<CodeReview> approveReview(String tenantId, UUID reviewId, String reviewerId, String comment) {
        SubmitReviewInput input = new SubmitReviewInput(reviewerId, ReviewDecision.APPROVE, comment);
        return submitReview(tenantId, reviewId, input);
    }

    /**
     * Merges a review.
     */
    public Promise<CodeReview> mergeReview(String tenantId, UUID reviewId) {
        return repository.findById(tenantId, reviewId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Review not found"));
                    }
                    CodeReview review = opt.get();

                    review.merge();
                    return repository.save(review);
                });
    }

    /**
     * Closes a review without merging.
     */
    public Promise<CodeReview> closeReview(String tenantId, UUID reviewId) {
        return repository.findById(tenantId, reviewId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Review not found"));
                    }
                    CodeReview review = opt.get();

                    review.close();
                    return repository.save(review);
                });
    }

    /**
     * Gets review statistics for a project.
     */
    public Promise<ReviewStatistics> getProjectStatistics(String tenantId, String projectId) {
        return repository.findByProject(tenantId, projectId)
                .map(reviews -> {
                    int total = reviews.size();
                    int open = (int) reviews.stream().filter(r -> r.getStatus() == ReviewStatus.OPEN).count();
                    int inReview = (int) reviews.stream().filter(r -> r.getStatus() == ReviewStatus.IN_REVIEW).count();
                    int approved = (int) reviews.stream().filter(r -> r.getStatus() == ReviewStatus.APPROVED).count();
                    int merged = (int) reviews.stream().filter(r -> r.getStatus() == ReviewStatus.MERGED).count();
                    int closed = (int) reviews.stream().filter(r -> r.getStatus() == ReviewStatus.CLOSED).count();

                    double avgReviewTime = reviews.stream()
                            .mapToLong(r -> r.getMetrics().getReviewTimeMs())
                            .average()
                            .orElse(0);

                    return new ReviewStatistics(total, open, inReview, approved, merged, closed, avgReviewTime);
                });
    }

    // ========== Input/Output DTOs ==========

    public record CreateReviewInput(
            String projectId,
            String storyId,
            String pullRequestUrl,
            int pullRequestNumber,
            String title,
            String description,
            String authorId,
            boolean isDraft,
            List<String> reviewerIds,
            List<FileChange> fileChanges
    ) {}

    public record SubmitReviewInput(
            String reviewerId,
            ReviewDecision decision,
            String comment
    ) {}

    public record AddCommentInput(
            String authorId,
            String authorName,
            CommentType type,
            String content,
            String filePath,
            Integer lineNumber,
            String suggestionCode
    ) {}

    public record ReviewStatistics(
            int total,
            int open,
            int inReview,
            int approved,
            int merged,
            int closed,
            double avgReviewTimeMs
    ) {}
}
