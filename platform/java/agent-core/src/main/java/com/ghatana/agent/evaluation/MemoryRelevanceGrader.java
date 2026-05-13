/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Grader for memory relevance dimension.
 *
 * @doc.type interface
 * @doc.purpose Memory relevance grading
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public interface MemoryRelevanceGrader {

    /**
     * Grades memory relevance from execution data.
     *
     * @param executionData execution data including memory access, query relevance
     * @return memory relevance grade
     */
    @NotNull
    TraceGrade.MemoryRelevanceGrade grade(@NotNull Map<String, Object> executionData);
}
