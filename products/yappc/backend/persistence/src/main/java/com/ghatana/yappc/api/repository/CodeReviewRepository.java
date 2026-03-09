/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.CodeReview;
import com.ghatana.yappc.api.domain.CodeReview.ReviewStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CodeReview entities.
 *
 * @doc.type interface
 * @doc.purpose Repository for code review persistence
 * @doc.layer domain
 * @doc.pattern Repository
 */
public interface CodeReviewRepository {

    Promise<CodeReview> save(CodeReview review);

    Promise<Optional<CodeReview>> findById(String tenantId, UUID id);

    Promise<List<CodeReview>> findByProject(String tenantId, String projectId);

    Promise<List<CodeReview>> findByStory(String tenantId, String storyId);

    Promise<List<CodeReview>> findByAuthor(String tenantId, String authorId);

    Promise<List<CodeReview>> findByReviewer(String tenantId, String reviewerId);

    Promise<List<CodeReview>> findByStatus(String tenantId, String projectId, ReviewStatus status);

    Promise<List<CodeReview>> findPendingForUser(String tenantId, String userId);

    Promise<Boolean> delete(String tenantId, UUID id);
}
