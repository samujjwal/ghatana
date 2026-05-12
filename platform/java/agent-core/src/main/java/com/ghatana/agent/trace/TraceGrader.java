/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.trace;

import org.jetbrains.annotations.NotNull;

/**
 * Grader for traces based on quality and relevance for learning.
 *
 * @doc.type interface
 * @doc.purpose Grader for trace quality and relevance
 * @doc.layer agent-core
 * @doc.pattern Grader
 */
public interface TraceGrader {

    /**
     * Grades a trace based on quality and relevance.
     *
     * @param trace trace to grade
     * @return trace grade
     */
    @NotNull
    TraceGrade grade(@NotNull Trace trace);

    /**
     * Returns the score for a grade.
     *
     * @param grade trace grade
     * @return score (0.0 to 1.0)
     */
    double score(@NotNull TraceGrade grade);
}
