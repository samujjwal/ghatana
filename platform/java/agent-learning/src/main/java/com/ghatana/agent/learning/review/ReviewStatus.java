/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.learning.review;

/**
 * Status of a review item in the human review queue.
 *
 * @doc.type enum
 * @doc.purpose Review item lifecycle status
 * @doc.layer agent-learning
 * @doc.pattern ValueObject
 *
 * @since 2.4.0
 */
public enum ReviewStatus {

    /** Awaiting assignment to a reviewer. */
    PENDING,

    /** Assigned to a reviewer and under evaluation. */
    IN_REVIEW,

    /** Approved by a human reviewer. */
    APPROVED,

    /** Rejected by a human reviewer. */
    REJECTED,

    /** Automatically expired after exceeding review SLA. */
    EXPIRED
}
