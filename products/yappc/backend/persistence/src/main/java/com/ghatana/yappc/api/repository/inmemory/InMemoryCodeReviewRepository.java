/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.inmemory;

import com.ghatana.yappc.api.domain.CodeReview;
import com.ghatana.yappc.api.domain.CodeReview.*;
import com.ghatana.yappc.api.repository.CodeReviewRepository;
import io.activej.promise.Promise;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of CodeReviewRepository.
 *
 * @doc.type class
 * @doc.purpose In-memory storage for code reviews
 * @doc.layer repository
 * @doc.pattern Repository
 */
public class InMemoryCodeReviewRepository implements CodeReviewRepository {

    private final Map<String, Map<UUID, CodeReview>> tenantReviews = new ConcurrentHashMap<>();

    private Map<UUID, CodeReview> getReviewMap(String tenantId) {
        return tenantReviews.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
    }

    @Override
    public Promise<CodeReview> save(CodeReview review) {
        if (review.getId() == null) {
            review.setId(UUID.randomUUID());
        }
        getReviewMap(review.getTenantId()).put(review.getId(), review);
        return Promise.of(review);
    }

    @Override
    public Promise<Optional<CodeReview>> findById(String tenantId, UUID id) {
        return Promise.of(Optional.ofNullable(getReviewMap(tenantId).get(id)));
    }

    @Override
    public Promise<List<CodeReview>> findByProject(String tenantId, String projectId) {
        return Promise.of(
            getReviewMap(tenantId).values().stream()
                .filter(r -> projectId.equals(r.getProjectId()))
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<CodeReview>> findByStory(String tenantId, String storyId) {
        return Promise.of(
            getReviewMap(tenantId).values().stream()
                .filter(r -> storyId.equals(r.getStoryId()))
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<CodeReview>> findByAuthor(String tenantId, String authorId) {
        return Promise.of(
            getReviewMap(tenantId).values().stream()
                .filter(r -> authorId.equals(r.getAuthorId()))
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<CodeReview>> findByReviewer(String tenantId, String reviewerId) {
        return Promise.of(
            getReviewMap(tenantId).values().stream()
                .filter(r -> r.getReviewers() != null &&
                    r.getReviewers().stream().anyMatch(rev -> reviewerId.equals(rev.getUserId())))
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<CodeReview>> findByStatus(String tenantId, String projectId, ReviewStatus status) {
        return Promise.of(
            getReviewMap(tenantId).values().stream()
                .filter(r -> projectId.equals(r.getProjectId()) && status == r.getStatus())
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<CodeReview>> findPendingForUser(String tenantId, String userId) {
        return Promise.of(
            getReviewMap(tenantId).values().stream()
                .filter(r -> r.getStatus() == ReviewStatus.OPEN || r.getStatus() == ReviewStatus.IN_REVIEW)
                .filter(r -> r.getReviewers() != null &&
                    r.getReviewers().stream().anyMatch(rev -> userId.equals(rev.getUserId())))
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<Boolean> delete(String tenantId, UUID id) {
        CodeReview removed = getReviewMap(tenantId).remove(id);
        return Promise.of(removed != null);
    }
}
