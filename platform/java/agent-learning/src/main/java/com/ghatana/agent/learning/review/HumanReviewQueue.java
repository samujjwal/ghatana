/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.learning.review;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Queue for items requiring human review before promotion.
 *
 * <p>When evaluation gates produce low-confidence results (below threshold,
 * typically &lt;0.7), the corresponding update candidate is enqueued here
 * for human review rather than being auto-promoted or auto-rejected.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Evaluation gate flags item with recommendation "REVIEW"</li>
 *   <li>{@link #enqueue(ReviewItem)} puts it in the queue</li>
 *   <li>{@link ReviewNotificationSpi} notifies reviewers</li>
 *   <li>Human reviewer calls {@link #approve(String, ReviewDecision)}
 *       or {@link #reject(String, ReviewDecision)}</li>
 *   <li>System applies the decision (promote or discard)</li>
 * </ol>
 *
 * @doc.type interface
 * @doc.purpose Human review queue for low-confidence policies
 * @doc.layer agent-learning
 * @doc.pattern Repository / Queue
 *
 * @since 2.4.0
 */
public interface HumanReviewQueue {

    /**
     * Enqueues an item for human review.
     *
     * @param item the review item to enqueue
     * @return the persisted review item with assigned ID
     */
    @NotNull Promise<ReviewItem> enqueue(@NotNull ReviewItem item);

    /**
     * Retrieves pending review items, optionally filtered.
     *
     * @param filter optional filter criteria (null = all pending)
     * @return list of pending review items
     */
    @NotNull Promise<List<ReviewItem>> getPending(@Nullable ReviewFilter filter);

    /**
     * Gets a specific review item by ID.
     *
     * @param reviewId the review item ID
     * @return the review item, or null if not found
     */
    @NotNull Promise<@Nullable ReviewItem> getById(@NotNull String reviewId);

    /**
     * Approves a pending review item.
     *
     * @param reviewId the review item to approve
     * @param decision the approval decision with rationale
     * @return the updated review item
     */
    @NotNull Promise<ReviewItem> approve(@NotNull String reviewId, @NotNull ReviewDecision decision);

    /**
     * Rejects a pending review item.
     *
     * @param reviewId the review item to reject
     * @param decision the rejection decision with rationale
     * @return the updated review item
     */
    @NotNull Promise<ReviewItem> reject(@NotNull String reviewId, @NotNull ReviewDecision decision);

    /**
     * Returns the count of pending review items.
     *
     * @return count of items awaiting review
     */
    @NotNull Promise<Long> pendingCount();
}
