/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.learning.review;

import org.jetbrains.annotations.NotNull;

/**
 * SPI for notifying external systems about review items.
 *
 * <p>Implementations may send webhook calls, email notifications, Slack messages,
 * or integrate with ticketing systems (Jira, Linear, etc.).
 *
 * @doc.type interface
 * @doc.purpose Review notification SPI for external systems
 * @doc.layer agent-learning
 * @doc.pattern Strategy / SPI
 *
 * @since 2.4.0
 */
public interface ReviewNotificationSpi {

    /**
     * Called when a new item is enqueued for review.
     *
     * @param item the newly enqueued review item
     */
    void onItemEnqueued(@NotNull ReviewItem item);

    /**
     * Called when a review item is approved.
     *
     * @param item the approved review item
     */
    void onItemApproved(@NotNull ReviewItem item);

    /**
     * Called when a review item is rejected.
     *
     * @param item the rejected review item
     */
    void onItemRejected(@NotNull ReviewItem item);

    /**
     * No-op implementation for when notifications are not needed.
     */
    ReviewNotificationSpi NOOP = new ReviewNotificationSpi() {
        @Override public void onItemEnqueued(@NotNull ReviewItem item) {}
        @Override public void onItemApproved(@NotNull ReviewItem item) {}
        @Override public void onItemRejected(@NotNull ReviewItem item) {}
    };
}
