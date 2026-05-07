/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.learning.review;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
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
            .map(this::expireIfNeeded)
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
    public Promise<List<ReviewItem>> listRecent(@Nullable ReviewFilter filter) {
        var stream = items.values().stream()
            .map(this::expireIfNeeded)
            .filter(item -> matchesFilter(item, filter))
            .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()));

        if (filter != null && filter.limit() > 0) {
            stream = stream.limit(filter.limit());
        }

        return Promise.of(stream.collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<@Nullable ReviewItem> getById(@NotNull String reviewId) {
        ReviewItem item = items.get(reviewId);
        return Promise.of(item != null ? expireIfNeeded(item) : null);
    }

    @Override
    @NotNull
    public Promise<ReviewItem> approve(@NotNull String reviewId, @NotNull ReviewDecision decision) {
        ReviewItem item = items.get(reviewId);
        if (item == null) {
            return Promise.ofException(
                    new IllegalArgumentException("Review item not found: " + reviewId));
        }
        item = expireIfNeeded(item);
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
        item = expireIfNeeded(item);
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
    public Promise<ReviewItem> escalate(@NotNull String reviewId) {
        ReviewItem item = items.get(reviewId);
        if (item == null) {
            return Promise.ofException(
                    new IllegalArgumentException("Review item not found: " + reviewId));
        }
        item = expireIfNeeded(item);
        if (item.getStatus() != ReviewStatus.PENDING && item.getStatus() != ReviewStatus.IN_REVIEW) {
            return Promise.ofException(
                    new IllegalStateException("Review item " + reviewId + " cannot be escalated from state: " + item.getStatus()));
        }
        item.markEscalated();
        notificationSpi.onItemEscalated(item);
        return Promise.of(item);
    }

    @Override
    @NotNull
    public Promise<List<ReviewItem>> findOverdue(long thresholdSeconds, @Nullable String tenantId) {
        Instant cutoff = Instant.now().minusSeconds(thresholdSeconds);
        List<ReviewItem> overdue = items.values().stream()
            .map(this::expireIfNeeded)
                .filter(i -> i.getStatus() == ReviewStatus.PENDING
                        || i.getStatus() == ReviewStatus.IN_REVIEW)
                .filter(i -> i.getCreatedAt().isBefore(cutoff))
                .filter(i -> tenantId == null || tenantId.equals(i.getTenantId()))
                .collect(Collectors.toList());
        return Promise.of(overdue);
    }

    @Override
    @NotNull
    public Promise<Long> pendingCount() {
        long count = items.values().stream()
                .map(this::expireIfNeeded)
                .filter(i -> i.getStatus() == ReviewStatus.PENDING
                        || i.getStatus() == ReviewStatus.IN_REVIEW)
                .count();
        return Promise.of(count);
    }

    private ReviewItem expireIfNeeded(ReviewItem item) {
        if ((item.getStatus() == ReviewStatus.PENDING || item.getStatus() == ReviewStatus.IN_REVIEW)
                && item.isExpired(Instant.now())) {
            item.markExpired();
        }
        return item;
    }

    private boolean matchesFilter(ReviewItem item, @Nullable ReviewFilter filter) {
        if (filter == null) {
            return true;
        }
        if (filter.tenantId() != null && !filter.tenantId().equals(item.getTenantId())) {
            return false;
        }
        if (filter.itemType() != null && filter.itemType() != item.getItemType()) {
            return false;
        }
        if (filter.maxConfidence() != null && item.getConfidenceScore() > filter.maxConfidence()) {
            return false;
        }
        return filter.assignedTo() == null || filter.assignedTo().equals(item.getAssignedTo());
    }
}
