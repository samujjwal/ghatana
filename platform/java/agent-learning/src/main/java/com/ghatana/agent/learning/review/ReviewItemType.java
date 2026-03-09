/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.learning.review;

/**
 * Types of items that can be submitted for human review.
 *
 * @doc.type enum
 * @doc.purpose Classifies review queue items
 * @doc.layer agent-learning
 * @doc.pattern ValueObject
 *
 * @since 2.4.0
 */
public enum ReviewItemType {

    /** A learned policy/procedure requiring validation. */
    POLICY,

    /** A skill version promotion candidate. */
    SKILL_PROMOTION,

    /** A fact extracted from episodes requiring verification. */
    FACT_EXTRACTION,

    /** An agent behavioral change requiring approval. */
    BEHAVIORAL_CHANGE,

    /** A conflict resolution requiring human judgment. */
    CONFLICT_RESOLUTION
}
