/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a code review.
 *
 * @doc.type class
 * @doc.purpose Code review domain entity for collaboration
 * @doc.layer domain
 * @doc.pattern Entity
 */
public class CodeReview {

    private UUID id;
    private String tenantId;
    private String projectId;
    private String storyId;
    private String pullRequestUrl;
    private int pullRequestNumber;
    private String title;
    private String description;
    private String authorId;
    private ReviewStatus status;
    private List<Reviewer> reviewers;
    private List<ReviewComment> comments;
    private List<FileChange> fileChanges;
    private ReviewMetrics metrics;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant mergedAt;
    private Map<String, Object> metadata;

    public CodeReview() {
        this.id = UUID.randomUUID();
        this.status = ReviewStatus.OPEN;
        this.reviewers = new ArrayList<>();
        this.comments = new ArrayList<>();
        this.fileChanges = new ArrayList<>();
        this.metrics = new ReviewMetrics();
        this.metadata = new HashMap<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ========== Enums ==========

    public enum ReviewStatus {
        DRAFT,
        OPEN,
        IN_REVIEW,
        CHANGES_REQUESTED,
        APPROVED,
        MERGED,
        CLOSED
    }

    public enum ReviewDecision {
        APPROVE,
        REQUEST_CHANGES,
        COMMENT
    }

    public enum CommentType {
        GENERAL,
        INLINE,
        SUGGESTION,
        QUESTION,
        ISSUE
    }

    // ========== Nested Classes ==========

    public static class Reviewer {
        private String userId;
        private String displayName;
        private ReviewDecision decision;
        private Instant reviewedAt;
        private boolean required;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public ReviewDecision getDecision() { return decision; }
        public void setDecision(ReviewDecision decision) { this.decision = decision; }

        public Instant getReviewedAt() { return reviewedAt; }
        public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
    }

    public static class ReviewComment {
        private String id;
        private String authorId;
        private String authorName;
        private CommentType type;
        private String content;
        private String filePath;
        private Integer lineNumber;
        private String suggestionCode;
        private boolean resolved;
        private String resolvedBy;
        private Instant createdAt;
        private Instant updatedAt;
        private List<ReviewComment> replies;

        public ReviewComment() {
            this.id = UUID.randomUUID().toString();
            this.replies = new ArrayList<>();
            this.createdAt = Instant.now();
            this.updatedAt = Instant.now();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getAuthorId() { return authorId; }
        public void setAuthorId(String authorId) { this.authorId = authorId; }

        public String getAuthorName() { return authorName; }
        public void setAuthorName(String authorName) { this.authorName = authorName; }

        public CommentType getType() { return type; }
        public void setType(CommentType type) { this.type = type; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public Integer getLineNumber() { return lineNumber; }
        public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }

        public String getSuggestionCode() { return suggestionCode; }
        public void setSuggestionCode(String suggestionCode) { this.suggestionCode = suggestionCode; }

        public boolean isResolved() { return resolved; }
        public void setResolved(boolean resolved) { this.resolved = resolved; }

        public String getResolvedBy() { return resolvedBy; }
        public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

        public List<ReviewComment> getReplies() { return replies; }
        public void setReplies(List<ReviewComment> replies) { this.replies = replies; }
    }

    public static class FileChange {
        private String filePath;
        private String changeType;  // added, modified, deleted, renamed
        private int additions;
        private int deletions;
        private String oldPath;     // for renames

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public String getChangeType() { return changeType; }
        public void setChangeType(String changeType) { this.changeType = changeType; }

        public int getAdditions() { return additions; }
        public void setAdditions(int additions) { this.additions = additions; }

        public int getDeletions() { return deletions; }
        public void setDeletions(int deletions) { this.deletions = deletions; }

        public String getOldPath() { return oldPath; }
        public void setOldPath(String oldPath) { this.oldPath = oldPath; }
    }

    public static class ReviewMetrics {
        private int totalComments;
        private int resolvedComments;
        private int totalFiles;
        private int additions;
        private int deletions;
        private long reviewTimeMs;
        private double complexityScore;

        public int getTotalComments() { return totalComments; }
        public void setTotalComments(int totalComments) { this.totalComments = totalComments; }

        public int getResolvedComments() { return resolvedComments; }
        public void setResolvedComments(int resolvedComments) { this.resolvedComments = resolvedComments; }

        public int getTotalFiles() { return totalFiles; }
        public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }

        public int getAdditions() { return additions; }
        public void setAdditions(int additions) { this.additions = additions; }

        public int getDeletions() { return deletions; }
        public void setDeletions(int deletions) { this.deletions = deletions; }

        public long getReviewTimeMs() { return reviewTimeMs; }
        public void setReviewTimeMs(long reviewTimeMs) { this.reviewTimeMs = reviewTimeMs; }

        public double getComplexityScore() { return complexityScore; }
        public void setComplexityScore(double complexityScore) { this.complexityScore = complexityScore; }
    }

    // ========== Domain Methods ==========

    public void addReviewer(Reviewer reviewer) {
        if (reviewers.stream().noneMatch(r -> r.getUserId().equals(reviewer.getUserId()))) {
            reviewers.add(reviewer);
            this.updatedAt = Instant.now();
        }
    }

    public void addComment(ReviewComment comment) {
        comments.add(comment);
        metrics.setTotalComments(comments.size());
        this.updatedAt = Instant.now();
    }

    public void submitReview(String userId, ReviewDecision decision) {
        reviewers.stream()
                .filter(r -> r.getUserId().equals(userId))
                .findFirst()
                .ifPresent(r -> {
                    r.setDecision(decision);
                    r.setReviewedAt(Instant.now());
                });
        updateStatusBasedOnReviews();
        this.updatedAt = Instant.now();
    }

    private void updateStatusBasedOnReviews() {
        boolean allApproved = reviewers.stream()
                .filter(Reviewer::isRequired)
                .allMatch(r -> r.getDecision() == ReviewDecision.APPROVE);
        boolean hasChangesRequested = reviewers.stream()
                .anyMatch(r -> r.getDecision() == ReviewDecision.REQUEST_CHANGES);

        if (hasChangesRequested) {
            this.status = ReviewStatus.CHANGES_REQUESTED;
        } else if (allApproved) {
            this.status = ReviewStatus.APPROVED;
        } else {
            this.status = ReviewStatus.IN_REVIEW;
        }
    }

    public void merge() {
        if (status != ReviewStatus.APPROVED) {
            throw new IllegalStateException("Cannot merge - review not approved");
        }
        this.status = ReviewStatus.MERGED;
        this.mergedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void close() {
        this.status = ReviewStatus.CLOSED;
        this.updatedAt = Instant.now();
    }

    // ========== Getters and Setters ==========

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getStoryId() { return storyId; }
    public void setStoryId(String storyId) { this.storyId = storyId; }

    public String getPullRequestUrl() { return pullRequestUrl; }
    public void setPullRequestUrl(String pullRequestUrl) { this.pullRequestUrl = pullRequestUrl; }

    public int getPullRequestNumber() { return pullRequestNumber; }
    public void setPullRequestNumber(int pullRequestNumber) { this.pullRequestNumber = pullRequestNumber; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public ReviewStatus getStatus() { return status; }
    public void setStatus(ReviewStatus status) { this.status = status; }

    public List<Reviewer> getReviewers() { return reviewers; }
    public void setReviewers(List<Reviewer> reviewers) { this.reviewers = reviewers; }

    public List<ReviewComment> getComments() { return comments; }
    public void setComments(List<ReviewComment> comments) { this.comments = comments; }

    public List<FileChange> getFileChanges() { return fileChanges; }
    public void setFileChanges(List<FileChange> fileChanges) { this.fileChanges = fileChanges; }

    public ReviewMetrics getMetrics() { return metrics; }
    public void setMetrics(ReviewMetrics metrics) { this.metrics = metrics; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getMergedAt() { return mergedAt; }
    public void setMergedAt(Instant mergedAt) { this.mergedAt = mergedAt; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CodeReview)) return false;
        CodeReview that = (CodeReview) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
