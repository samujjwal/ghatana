/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.composition;

/**
 * Strategy for aggregating outputs from agents in a
 * {@link CompositionPattern#SCATTER_GATHER} composition.
 *
 * @doc.type enum
 * @doc.purpose Aggregation strategy for scatter-gather compositions
 * @doc.layer platform
 * @doc.pattern Enum
 */
public enum AggregationStrategy {
    /** Collect all results into a list and return them as the group output. */
    COLLECT_ALL,
    /** Return the first successful result; discard the rest. */
    FIRST_SUCCESS,
    /** Return the result with the highest confidence score. */
    HIGHEST_CONFIDENCE,
    /** Merge all results using domain-specific merge logic provided by the caller. */
    CUSTOM_MERGE
}
