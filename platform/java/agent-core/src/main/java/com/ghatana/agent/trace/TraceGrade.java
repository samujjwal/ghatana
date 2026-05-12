/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.trace;

/**
 * Grade for a trace based on quality and relevance for learning.
 *
 * @doc.type enum
 * @doc.purpose Grade for trace quality and relevance
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum TraceGrade {
    /**
     * Excellent trace - high quality, high relevance, clear causality.
     */
    EXCELLENT,

    /**
     * Good trace - high quality, moderate relevance.
     */
    GOOD,

    /**
     * Fair trace - moderate quality and relevance.
     */
    FAIR,

    /**
     * Poor trace - low quality or low relevance.
     */
    POOR,

    /**
     * Invalid trace - corrupted, incomplete, or unusable.
     */
    INVALID
}
