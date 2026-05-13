/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

/**
 * Novelty classification for a task based on familiarity and historical execution.
 *
 * @doc.type enum
 * @doc.purpose Novelty classification for tasks
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum TaskNovelty {
    /**
     * Novelty level is unknown or not yet classified.
     */
    UNKNOWN,

    /**
     * Task is well-known and frequently executed.
     */
    FAMILIAR,

    /**
     * Task is somewhat known but infrequently executed.
     */
    SEMI_FAMILIAR,

    /**
     * Task is novel but similar to known tasks.
     */
    SIMILAR,

    /**
     * Task is completely novel with no similar precedents.
     */
    NOVEL
}
