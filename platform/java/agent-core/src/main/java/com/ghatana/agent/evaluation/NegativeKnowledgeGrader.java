/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Grader for negative knowledge usage dimension.
 *
 * @doc.type interface
 * @doc.purpose Negative knowledge grading
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public interface NegativeKnowledgeGrader {

    /**
     * Grades negative knowledge usage from execution data.
     *
     * @param executionData execution data including negative knowledge access, failure patterns
     * @return negative knowledge grade
     */
    @NotNull
    TraceGrade.NegativeKnowledgeGrade grade(@NotNull Map<String, Object> executionData);
}
