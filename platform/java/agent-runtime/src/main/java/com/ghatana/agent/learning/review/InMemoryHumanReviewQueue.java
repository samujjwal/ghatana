/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.learning.review;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link HumanReviewQueue} for testing and
 * development. Production deployments should use a persistent implementation
 * backed by the platform database.
 *
 * @doc.type class
 * @doc.purpose In-memory human review queue implementation
 * @doc.layer agent-learning
 * @doc.pattern Repository
 *
 * @since 2.4.0
 */
public final class InMemoryHumanReviewQueue implements HumanReviewQueue {

    private final Map<String, ReviewItem> items = new ConcurrentHashMap<>();
    private final ReviewNotificationSpi notificationSpi;

    /**
     * Creates a new in-memory review queue.
     *
     * @param notificationSpi notification SPI (use {@link ReviewNotificationSpi#NOOP} for none)
     */
    public InMemoryHumanReviewQueue(@NotNull ReviewNotificationSpi notificationSpi) {
        this.notificationSpi = Objects.requireNonNull(notificationSpi);
    }

    /** Creates a queue with no notifications. */
    public InMemoryHumanReviewQueue() {
        this(ReviewNotificationSpi.NOOP);
    }

    @Override
    @NotNull
    public Promise<ReviewItem> enqueue(@NotNull ReviewItem item) {
        items.put(item.getReviewId(), item);
        notificationSpi.onItemEnqueued(item);
        return Promise.of(item);
    }

    @Override
    @NotNull
    public Promise<List<ReviewItem>> getPending(@Nullable ReviewFilter filter) {
        var stream = items.values().stream()
                .filter(item -> item.getStatus() == ReviewStatus.PENDING
                        || item.getStatus() == ReviewStatus.IN_REVIEW);

        if (filter != null) {
            if (filter.tenantId() != null) {
                stream = stream.filter(i -> i.getTenantId().equals(filter.tenantId()));
            }
            if (filter.itemType() != null) {
                stream = stream.filter(i -> i.getItemType() == filter.itemType());
            }
            if (filter.maxConfidence() != null) {
                stream = stream.filter(i -> i.getConfidenceScore() <= filter.maxConfidence());
            }
            if (filter.assignedTo() != null) {
                stream = stream.filter(i -> filter.assignedTo().equals(i.getAssignedTo()));
            }
            if (filter.limit() > 0) {
                stream = stream.limit(filter.limit());
            }
        }

        return Promise.of(stream.collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<@Nullable ReviewItem> getById(@NotNull String reviewId) {
        return Promise.of(items.get(reviewId));
    }

    @Override
    @NotNull
    public Promise<ReviewItem> approve(@NotNull String reviewId, @NotNull ReviewDecision decision) {
        ReviewItem item = items.get(reviewId);
        if (item == null) {
            return Promise.ofException(
                    new IllegalArgumentException("Review item not found: " + reviewId));
        }
        if (item.getStatus() != ReviewStatus.PENDING && item.getStatus() != ReviewStatus.IN_REVIEW) {
            return Promise.ofException(
                    new IllegalStateException("Review item " + reviewId + " is not pending: " + item.getStatus()));
        }

        item.markApproved(decision);
        notificationSpi.onItemApproved(item);
        return Promise.of(item);
    }

    @Override
    @NotNull
    public Promise<ReviewItem> reject(@NotNull String reviewId, @NotNull ReviewDecision decision) {
        ReviewItem item = items.get(reviewId);
        if (item == null) {
            return Promise.ofException(
                    new IllegalArgumentException("Review item not found: " + reviewId));
        }
        if (item.getStatus() != ReviewStatus.PENDING && item.getStatus() != ReviewStatus.IN_REVIEW) {
            return Promise.ofException(
                    new IllegalStateException("Review item " + reviewId + " is not pending: " + item.getStatus()));
        }

        item.markRejected(decision);
        notificationSpi.onItemRejected(item);
        return Promise.of(item);
    }

    @Override
    @NotNull
    public Promise<Long> pendingCount() {
        long count = items.values().stream()
                .filter(i -> i.getStatus() == ReviewStatus.PENDING
                        || i.getStatus() == ReviewStatus.IN_REVIEW)
                .count();
        return Promise.of(count);
    }
}
