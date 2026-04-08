/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.composition;

/**
 * Structural pattern used to compose multiple agents.
 *
 * @doc.type enum
 * @doc.purpose Composition pattern taxonomy for multi-agent orchestration
 * @doc.layer platform
 * @doc.pattern Enum
 */
public enum CompositionPattern {
    /** Pipeline — agents are chained; output of one becomes input of the next. */
    PIPELINE,
    /** Parallel fan-out with aggregation of individual results. */
    SCATTER_GATHER,
    /** Majority or weighted voting across independent parallel agents. */
    VOTING,
    /** Map-reduce; agents process partitions, results are merged. */
    MAP_REDUCE,
    /** Fallback chain; try agents in order until one succeeds. */
    FALLBACK_CHAIN,
    /** Round-robin load distribution across equivalent agents. */
    ROUND_ROBIN
}
