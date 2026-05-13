/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Grader for tool correctness dimension.
 *
 * @doc.type interface
 * @doc.purpose Tool correctness grading
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public interface ToolCorrectnessGrader {

    /**
     * Grades tool correctness from execution data.
     *
     * @param executionData execution data including tool calls, expected tool calls
     * @return tool correctness grade
     */
    @NotNull
    TraceGrade.ToolCorrectnessGrade grade(@NotNull Map<String, Object> executionData);
}
